package dev.keynest.policy.architecturefixtures;

import dev.keynest.manager.ManagerApiApplication;
import org.springframework.stereotype.Service;

/** Deliberately invalid test bytecode; never part of a library or component scan. */
@Service
public class ManagerDependentPolicy {
  private ManagerApiApplication manager;
}
