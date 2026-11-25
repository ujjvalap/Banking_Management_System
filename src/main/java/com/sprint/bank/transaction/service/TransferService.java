package com.sprint.bank.transaction.service;

import com.sprint.bank.transaction.dto.TransactionResponse;
import com.sprint.bank.transaction.dto.TransferRequest;

public interface TransferService {

    TransactionResponse transfer(TransferRequest request);
}
