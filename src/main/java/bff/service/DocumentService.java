package bff.service;

import bff.dto.DocumentRequest;

public interface DocumentService {

    byte[] generate(String jwt, DocumentRequest request);
}
