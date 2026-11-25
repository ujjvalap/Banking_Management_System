document.addEventListener("DOMContentLoaded", async () => {
    const { get, put, showAlert, clearAlert, formatCurrency, formatDateTime, escapeHtml } = window.BankApi;

    if (document.getElementById("adminDashboardPage")) {
        try {
            const [summaryRes, txRes] = await Promise.all([
                get("/api/admin/summary"),
                get("/api/admin/transactions?page=0&size=8")
            ]);
            const s = summaryRes?.data;
            document.getElementById("adminTotalBalance").textContent = formatCurrency(s?.totalBalance || 0);
            document.getElementById("adminTotalCustomers").textContent = String(s?.totalCustomers || 0);
            document.getElementById("adminTotalAccounts").textContent = String(s?.totalAccounts || 0);
            document.getElementById("adminTotalTransactions").textContent = String(s?.totalTransactions || 0);

            const tbody = document.getElementById("adminRecentTxBody");
            const items = txRes?.data?.content || [];
            if (items.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No system transactions yet.</td></tr>';
            } else {
                tbody.innerHTML = items.map((tx) => `
                    <tr>
                        <td>${escapeHtml(tx.referenceId)}</td>
                        <td><strong>${escapeHtml(tx.accountNumber)}</strong></td>
                        <td><span class="status-badge status-${escapeHtml(tx.type).toLowerCase()}">${escapeHtml(tx.type)}</span></td>
                        <td>${formatCurrency(tx.amount)}</td>
                        <td>${escapeHtml(tx.description)}</td>
                        <td>${formatDateTime(tx.timestamp)}</td>
                    </tr>
                `).join("");
            }
        } catch (err) {
            showAlert("pageAlert", err.message || "Failed to load admin summary.", "error");
        }
    }

    const customersBody = document.getElementById("adminCustomersBody");
    if (customersBody) {
        try {
            const res = await get("/api/admin/customers");
            const customers = res?.data || [];
            if (customers.length === 0) {
                customersBody.innerHTML = '<tr><td colspan="7" class="empty-state">No customers registered.</td></tr>';
            } else {
                customersBody.innerHTML = customers.map((c) => `
                    <tr>
                        <td>${escapeHtml(c.id)}</td>
                        <td><strong>${escapeHtml(c.fullName)}</strong></td>
                        <td>${escapeHtml(c.email)}</td>
                        <td>${escapeHtml(c.phone)}</td>
                        <td>${escapeHtml(c.pan)}</td>
                        <td>${escapeHtml(c.accountCount)}</td>
                        <td><span class="status-badge">${c.enabled ? "VERIFIED" : "PENDING"}</span></td>
                    </tr>
                `).join("");
            }
        } catch (err) {
            showAlert("pageAlert", err.message || "Failed to load customers.", "error");
        }
    }

    const accountsBody = document.getElementById("adminAccountsBody");
    if (accountsBody) {
        const modal = document.getElementById("accountDetailModal");
        const modalBody = document.getElementById("accountDetailContent");
        document.getElementById("closeDetailModal")?.addEventListener("click", () => modal.classList.add("hidden"));

        async function loadAdminAccounts() {
            try {
                const res = await get("/api/admin/accounts");
                const accounts = res?.data || [];
                if (accounts.length === 0) {
                    accountsBody.innerHTML = '<tr><td colspan="6" class="empty-state">No accounts found.</td></tr>';
                    return;
                }
                accountsBody.innerHTML = accounts.map((acc) => `
                    <tr>
                        <td><strong>${escapeHtml(acc.accountNumber)}</strong></td>
                        <td>
                            <div>${escapeHtml(acc.customerName)}</div>
                            <small>${escapeHtml(acc.customerEmail)}</small>
                        </td>
                        <td>${escapeHtml(acc.type)}</td>
                        <td>${formatCurrency(acc.balance)}</td>
                        <td><span class="status-badge status-${escapeHtml(acc.status).toLowerCase()}">${escapeHtml(acc.status)}</span></td>
                        <td>
                            <div class="inline-actions">
                                <button type="button" class="btn btn-outline btn-sm" data-view-id="${escapeHtml(acc.id)}">Details</button>
                                ${acc.status === "ACTIVE" ? `<button type="button" class="btn btn-warning btn-sm" data-status-id="${escapeHtml(acc.id)}" data-target-status="FROZEN">Freeze</button>` : ""}
                                ${acc.status === "FROZEN" ? `<button type="button" class="btn btn-outline btn-sm" data-status-id="${escapeHtml(acc.id)}" data-target-status="ACTIVE">Activate</button>` : ""}
                                ${acc.status !== "CLOSED" ? `<button type="button" class="btn btn-danger btn-sm" data-status-id="${escapeHtml(acc.id)}" data-target-status="CLOSED">Close</button>` : ""}
                            </div>
                        </td>
                    </tr>
                `).join("");

                accountsBody.querySelectorAll("[data-view-id]").forEach((btn) => {
                    btn.addEventListener("click", async () => {
                        try {
                            const detailRes = await get(`/api/admin/accounts/${encodeURIComponent(btn.dataset.viewId)}`);
                            const d = detailRes?.data;
                            modalBody.innerHTML = `
                                <div class="detail-list">
                                    <div class="detail-item"><small>Account Number</small><span>${escapeHtml(d.accountNumber)}</span></div>
                                    <div class="detail-item"><small>Type / Status</small><span>${escapeHtml(d.type)} — ${escapeHtml(d.status)}</span></div>
                                    <div class="detail-item"><small>Balance</small><span>${formatCurrency(d.balance)}</span></div>
                                    <div class="detail-item"><small>Customer</small><span>${escapeHtml(d.customerName)} (${escapeHtml(d.customerEmail)})</span></div>
                                    <div class="detail-item"><small>Created At</small><span>${formatDateTime(d.createdAt)}</span></div>
                                </div>
                            `;
                            modal.classList.remove("hidden");
                        } catch (err) {
                            showAlert("pageAlert", err.message || "Unable to fetch account details.", "error");
                        }
                    });
                });

                accountsBody.querySelectorAll("[data-status-id]").forEach((btn) => {
                    btn.addEventListener("click", async () => {
                        clearAlert("pageAlert");
                        const id = btn.dataset.statusId;
                        const status = btn.dataset.targetStatus;
                        try {
                            await put(`/api/admin/accounts/${encodeURIComponent(id)}/status`, { status });
                            showAlert("pageAlert", `Account updated to ${status}.`, "success");
                            await loadAdminAccounts();
                        } catch (err) {
                            showAlert("pageAlert", err.message || "Status update failed.", "error");
                        }
                    });
                });
            } catch (err) {
                showAlert("pageAlert", err.message || "Failed to load accounts.", "error");
            }
        }

        await loadAdminAccounts();
    }

    const adminTxForm = document.getElementById("adminTxFilterForm");
    if (adminTxForm) {
        const tbody = document.getElementById("adminTransactionsBody");
        const pageInfo = document.getElementById("adminTxPageInfo");
        const prevBtn = document.getElementById("adminPrevPage");
        const nextBtn = document.getElementById("adminNextPage");
        let currentPage = 0;

        async function loadAdminTransactions(page = 0) {
            clearAlert("pageAlert");
            const query = new URLSearchParams({ page: String(page), size: "15" });
            const startDate = document.getElementById("adminStartDate").value;
            const endDate = document.getElementById("adminEndDate").value;
            const type = document.getElementById("adminTxType").value;
            if (startDate) query.set("startDate", startDate);
            if (endDate) query.set("endDate", endDate);
            if (type) query.set("type", type);

            try {
                const res = await get(`/api/admin/transactions?${query.toString()}`);
                const pageData = res?.data;
                const items = pageData?.content || [];
                currentPage = pageData?.page || 0;

                if (items.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No transactions found for selected filters.</td></tr>';
                } else {
                    tbody.innerHTML = items.map((tx) => `
                        <tr>
                            <td>${escapeHtml(tx.referenceId)}</td>
                            <td><strong>${escapeHtml(tx.accountNumber)}</strong></td>
                            <td><span class="status-badge status-${escapeHtml(tx.type).toLowerCase()}">${escapeHtml(tx.type)}</span></td>
                            <td>${formatCurrency(tx.amount)}</td>
                            <td>${escapeHtml(tx.description)}</td>
                            <td>${formatDateTime(tx.timestamp)}</td>
                        </tr>
                    `).join("");
                }

                const totalPages = Math.max(1, pageData?.totalPages || 1);
                pageInfo.textContent = `Page ${currentPage + 1} of ${totalPages} (${pageData?.totalElements || 0} total)`;
                prevBtn.disabled = Boolean(pageData?.first ?? true);
                nextBtn.disabled = Boolean(pageData?.last ?? true);
            } catch (err) {
                showAlert("pageAlert", err.message || "Failed to load transactions.", "error");
            }
        }

        await loadAdminTransactions(0);
        adminTxForm.addEventListener("submit", (event) => {
            event.preventDefault();
            loadAdminTransactions(0);
        });
        prevBtn?.addEventListener("click", () => loadAdminTransactions(Math.max(0, currentPage - 1)));
        nextBtn?.addEventListener("click", () => loadAdminTransactions(currentPage + 1));
    }
});
