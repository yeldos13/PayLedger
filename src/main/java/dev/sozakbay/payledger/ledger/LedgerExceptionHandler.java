package dev.sozakbay.payledger.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps ledger errors to RFC 9457 problem details. */
@RestControllerAdvice
class LedgerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LedgerExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail ruleViolated(LedgerException e) {
        log.atInfo().addKeyValue("reason", e.getMessage()).log("Request rejected by ledger rule");
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notFound(AccountNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Concurrent update, retry the request");
    }
}
