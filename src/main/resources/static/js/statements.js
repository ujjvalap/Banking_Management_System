document.addEventListener("DOMContentLoaded", async () => {
    const filterForm = document.getElementById("statementFilterForm");
    if (!filterForm) return;

    const { get, showAlert, clearAlert, formatCurrency, formatDateTime, escapeHtml } = window.BankApi;
    const accountSelect = document.getElementById("statementAccountId");
    const tbody = document.getElementById("statementsTableBody");
    const pageInfo = document.getElementById("statementPageInfo");
    const prevBtn = document.getElementById("prevStatementPage");
    const nextBtn = document.getElementById("nextStatementPage");

    let currentPage = 0;

    async function loadAccounts() {
        const res = await get("/api/accounts");
        const accounts = res?.data || [];
        if (accounts.length === 0) {
            accountSelect.innerHTML = '<option value="">No accounts found</option>';
            tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Open an account to view statements.</td></tr>';
            return null;
        }
        const preselected = new URLSearchParams(window.location.search).get("accountId");
        accountSelect.innerHTML = accounts.map((acc) => `
            <option value="${escapeHtml(acc.id)}" ${preselected === String(acc.id) ? "selected" : ""}>
                ${escapeHtml(acc.accountNumber)} (${escapeHtml(acc.type)} — ${formatCurrency(acc.balance)})
            </option>
        `).join("");
        return accountSelect.value;
    }

    async function loadStatement(page = 0) {
        clearAlert("pageAlert");
        const accountId = accountSelect.value;
        if (!accountId) return;

        const query = new URLSearchParams({ page: String(page), size: "10" });
        const startDate = document.getElementById("startDate").value;
        const endDate = document.getElementById("endDate").value;
        const type = document.getElementById("txType").value;
        if (startDate) query.set("startDate", startDate);
        if (endDate) query.set("endDate", endDate);
        if (type) query.set("type", type);

        try {
            const res = await get(`/api/accounts/${encodeURIComponent(accountId)}/transactions?${query.toString()}`);
            const pageData = res?.data;
            const items = pageData?.content || [];
            currentPage = pageData?.page || 0;

            if (items.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No transactions match the selected filters.</td></tr>';
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
            showAlert("pageAlert", err.message || "Unable to load account statement.", "error");
        }
    }

    try {
        const selected = await loadAccounts();
        if (selected) {
            await loadStatement(0);
        }
    } catch (err) {
        showAlert("pageAlert", err.message || "Unable to load accounts.", "error");
    }

    filterForm.addEventListener("submit", (event) => {
        event.preventDefault();
        loadStatement(0);
    });

    accountSelect.addEventListener("change", () => loadStatement(0));
    prevBtn?.addEventListener("click", () => loadStatement(Math.max(0, currentPage - 1)));
    nextBtn?.addEventListener("click", () => loadStatement(currentPage + 1));
});
