package ru.itis.repository;
import org.junit.jupiter.api.Test;
import ru.itis.util.ApiException;
import static org.junit.jupiter.api.Assertions.*;
class ScooterDistanceTest {
 private double meters(double x,double y,double sx,double sy)throws Exception {
  var method=Scooters.class.getDeclaredMethod("distanceMeters",double.class,double.class,double.class,double.class);
  method.setAccessible(true);return (double)method.invoke(null,x,y,sx,sy);
 }
 @Test void identicalLocationAndEquatorialDegree()throws Exception {
  assertEquals(0,meters(37.6,55.7,37.6,55.7));assertEquals(111195,meters(0,0,1,0),2);
 }
 @Test void geographicRankingDiffersFromDegreeGridNearPole()throws Exception {
  assertTrue(meters(0,80,2,80)<meters(0,80,0,81));
 }
 @Test void dateLineUsesShortDistance()throws Exception {assertEquals(22239,meters(179.9,0,-179.9,0),2);}
 @Test void antipodesRemainFinite()throws Exception {assertTrue(Double.isFinite(meters(0,0,180,0)));}
 @Test void invalidGpsRejectedBeforeDatabaseConnection() {
  assertEquals(400,assertThrows(ApiException.class,()->new Scooters().list("READY",181.0,0.0)).status);
  assertEquals(400,assertThrows(ApiException.class,()->new Scooters().list("READY",0.0,91.0)).status);
 }
 @Test void invalidAdminCoordinatesAndPlateRejectedBeforeDatabaseConnection() {
  for(String plate:new String[]{"", "a/b", "<script>", "A".repeat(65)})
   assertEquals(400,assertThrows(ApiException.class,()->new Scooters().create(plate,37.6,55.7)).status);
  assertEquals(400,assertThrows(ApiException.class,()->new Scooters().create("TEST",181,0)).status);
  assertEquals(400,assertThrows(ApiException.class,()->new Scooters().create("TEST",0,91)).status);
  assertEquals(400,assertThrows(ApiException.class,()->new Scooters().create("TEST",Double.NaN,0)).status);
 }
}
