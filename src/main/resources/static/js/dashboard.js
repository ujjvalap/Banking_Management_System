document.addEventListener("DOMContentLoaded", async () => {
    if (!document.getElementById("customerDashboardPage")) return;
    const { get, showAlert, formatCurrency, formatDateTime, escapeHtml } = window.BankApi;

    try {
        const [meRes, accountsRes, txRes] = await Promise.all([
            get("/api/auth/me"),
            get("/api/accounts"),
            get("/api/accounts/transactions/recent")
        ]);

        const user = meRes?.data;
        const accounts = accountsRes?.data || [];
        const transactions = txRes?.data || [];

        const totalBalance = accounts.reduce((sum, acc) => sum + Number(acc.balance || 0), 0);
        document.getElementById("welcomeName").textContent = user?.fullName || "Customer";
        document.getElementById("totalBalance").textContent = formatCurrency(totalBalance);
        document.getElementById("accountCount").textContent = String(accounts.length);
        document.getElementById("recentTxCount").textContent = String(transactions.length);

        const accountsBody = document.getElementById("dashboardAccountsBody");
        if (accounts.length === 0) {
            accountsBody.innerHTML = '<tr><td colspan="4" class="empty-state">No accounts yet. Open a savings or current account to begin.</td></tr>';
        } else {
            accountsBody.innerHTML = accounts.map((acc) => `
                <tr>
                    <td><strong>${escapeHtml(acc.accountNumber)}</strong></td>
                    <td>${escapeHtml(acc.type)}</td>
                    <td>${formatCurrency(acc.balance)}</td>
                    <td><span class="status-badge status-${escapeHtml(acc.status).toLowerCase()}">${escapeHtml(acc.status)}</span></td>
                </tr>
            `).join("");
        }

        const txBody = document.getElementById("dashboardTransactionsBody");
        if (transactions.length === 0) {
            txBody.innerHTML = '<tr><td colspan="6" class="empty-state">No transactions recorded yet.</td></tr>';
        } else {
            txBody.innerHTML = transactions.slice(0, 6).map((tx) => `
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
        showAlert("pageAlert", err.message || "Failed to load dashboard data.", "error");
    }
});
