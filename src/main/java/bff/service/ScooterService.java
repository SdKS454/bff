package bff.service;

import bff.dto.JsonResponse;
import bff.dto.NearestScooterRequest;

public interface ScooterService {

    JsonResponse list(String jwt, boolean readyOnly);

    JsonResponse nearest(String jwt, NearestScooterRequest request);

    JsonResponse findByNumberPlate(String jwt, String numberPlate);
}
