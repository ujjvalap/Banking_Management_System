package com.sprint.bank.account.service;

import com.sprint.bank.account.dto.AccountRequest;
import com.sprint.bank.account.dto.AccountResponse;
import com.sprint.bank.transaction.dto.DepositRequest;
import com.sprint.bank.transaction.dto.WithdrawRequest;

import java.util.List;

public interface AccountService {

    AccountResponse openAccount(AccountRequest request);

    List<AccountResponse> getMyAccounts();

    AccountResponse getAccount(Long accountId);

    AccountResponse deposit(Long accountId, DepositRequest request);

    AccountResponse withdraw(Long accountId, WithdrawRequest request);
}
