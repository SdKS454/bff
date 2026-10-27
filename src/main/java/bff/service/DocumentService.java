package bff.service;

import bff.dto.DocumentRequest;

public interface DocumentService {

    bff.dto.JsonResponse metadata(String jwt,String method,String path);
    byte[] download(String jwt,java.util.UUID id);

    byte[] generate(String jwt, DocumentRequest request);
}
