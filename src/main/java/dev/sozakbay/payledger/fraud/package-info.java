/** Fraud checks. Knows the ledger only through the events it publishes. */
@ApplicationModule(allowedDependencies = "events")
package dev.sozakbay.payledger.fraud;

import org.springframework.modulith.ApplicationModule;
