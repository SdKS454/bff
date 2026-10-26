package ru.itis.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.itis.service.impl.RideServiceImpl;
import ru.itis.repository.RideRepository;
import ru.itis.model.*;
import ru.itis.exception.ApiException;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RideWorkflowTest {
 @ParameterizedTest @CsvSource({"0,58.00","1,58.00","59,58.00","60,58.00","61,66.00","120,66.00","121,74.00"})
 void billsStartedMinutesUsingTariffSnapshot(long seconds,String expected){
  var ride=new RideEntity();ride.setUnlockFee(new BigDecimal("50"));ride.setPricePerMinute(new BigDecimal("8"));
  assertEquals(new BigDecimal(expected),new RideIntegrationService().calculatePrice(ride,seconds));
 }
 @Test void failedReservationRemainsDurablyCancellable(){
  var repo=mock(RideRepository.class);var integration=mock(RideIntegrationService.class);
  when(integration.getRate(null)).thenReturn(new Rate(UUID.randomUUID(),BigDecimal.ONE,BigDecimal.TEN,"RUB"));
  doThrow(new ApiException(503,"offline")).when(integration).reserve(any());
  var service=new RideServiceImpl(repo,integration);
  assertEquals(503,assertThrows(ApiException.class,()->service.start(UUID.randomUUID(),"A",null,1.0,2.0)).getStatus());
  var order=inOrder(repo,integration);order.verify(repo).save(any());order.verify(integration).reserve(any());order.verify(repo).cancel(any());
 }
 @Test void recoveryNeverReleasesAnotherReservationsState(){
  var repo=mock(RideRepository.class);var integration=mock(RideIntegrationService.class);var ride=new RideEntity();ride.setId(UUID.randomUUID());ride.setStatus("CANCELLING");
  when(repo.pending()).thenReturn(List.of(ride));doThrow(new ApiException(409,"new reservation")).when(integration).release(ride,false);
  new RideServiceImpl(repo,integration).recover();verify(repo).removeCancelled(ride.getId());
 }
 @Test void lostReleaseResponseKeepsFinishForRetry(){
  var repo=mock(RideRepository.class);var integration=mock(RideIntegrationService.class);var ride=new RideEntity();ride.setId(UUID.randomUUID());ride.setStatus("FINISHING");
  when(repo.pending()).thenReturn(List.of(ride));doThrow(new ApiException(503,"offline")).when(integration).release(ride,true);
  new RideServiceImpl(repo,integration).recover();verify(repo,never()).completeFinish(any());
 }
 @Test void foreignRideNeverReachesPayment(){
  var repo=mock(RideRepository.class);var integration=mock(RideIntegrationService.class);var ride=new RideEntity();ride.setId(UUID.randomUUID());ride.setUserId(UUID.randomUUID());
  when(repo.findById(ride.getId())).thenReturn(Optional.of(ride));
  assertEquals(403,assertThrows(ApiException.class,()->new RideServiceImpl(repo,integration).pay(UUID.randomUUID(),ride.getId())).getStatus());verifyNoInteractions(integration);
 }
 @Test void competingFinishWithDifferentCoordinatesIsConflict(){
  var repo=mock(RideRepository.class);var integration=mock(RideIntegrationService.class);
  var id=UUID.randomUUID();var owner=UUID.randomUUID();
  var active=new RideEntity();active.setId(id);active.setUserId(owner);active.setStatus("ACTIVE");active.setStartedAt(Instant.now().minusSeconds(1));
  var completed=new RideEntity();completed.setId(id);completed.setUserId(owner);completed.setStatus("FINISHED");completed.setEndPosX(1.0);completed.setEndPosY(2.0);
  when(repo.findById(id)).thenReturn(Optional.of(active),Optional.of(completed));when(repo.finish(any())).thenReturn(false);
  assertEquals(409,assertThrows(ApiException.class,()->new RideServiceImpl(repo,integration).finish(owner,id,3,4)).getStatus());
  verify(integration,never()).release(any(),anyBoolean());
 }

}
