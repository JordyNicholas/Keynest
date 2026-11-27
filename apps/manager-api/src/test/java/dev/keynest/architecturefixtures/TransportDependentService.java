package dev.keynest.architecturefixtures;

import dev.keynest.manager.api.ApiPaths;
import dev.keynest.manager.auth.AuthController;
import org.springframework.stereotype.Service;

/** Deliberately invalid test bytecode; imported explicitly only by negative rule checks. */
@Service
public class TransportDependentService {
  private ApiPaths paths;
  private AuthController controller;
}
