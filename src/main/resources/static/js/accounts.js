document.addEventListener("DOMContentLoaded", async () => {
    const accountsTable = document.getElementById("accountsTableBody");
    const openAccountForm = document.getElementById("openAccountForm");
    if (!accountsTable && !openAccountForm) return;

    const { get, post, showAlert, clearAlert, setLoading, formatCurrency, formatDateTime, escapeHtml } = window.BankApi;

    async function loadAccounts() {
        if (!accountsTable) return;
        try {
            const res = await get("/api/accounts");
            const accounts = res?.data || [];
            if (accounts.length === 0) {
                accountsTable.innerHTML = '<tr><td colspan="6" class="empty-state">You have no bank accounts yet.</td></tr>';
                return;
            }
            accountsTable.innerHTML = accounts.map((acc) => `
                <tr>
                    <td><strong>${escapeHtml(acc.accountNumber)}</strong></td>
                    <td>${escapeHtml(acc.type)}</td>
                    <td>${formatCurrency(acc.balance)}</td>
                    <td><span class="status-badge status-${escapeHtml(acc.status).toLowerCase()}">${escapeHtml(acc.status)}</span></td>
                    <td>${formatDateTime(acc.createdAt)}</td>
                    <td>
                        <div class="inline-actions">
                            <a href="/customer/statements?accountId=${encodeURIComponent(acc.id)}" class="btn btn-outline btn-sm">Statement</a>
                            ${acc.status === "ACTIVE" ? `<a href="/customer/deposit?accountId=${encodeURIComponent(acc.id)}" class="btn btn-outline btn-sm">Deposit</a>` : ""}
                        </div>
                    </td>
                </tr>
            `).join("");
        } catch (err) {
            showAlert("pageAlert", err.message || "Unable to load accounts.", "error");
        }
    }

    await loadAccounts();

    openAccountForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearAlert("pageAlert");
        const btn = openAccountForm.querySelector('button[type="submit"]');
        setLoading(btn, true, "Opening account...");
        const form = new FormData(openAccountForm);

        try {
            const res = await post("/api/accounts", { type: form.get("type") });
            const created = res?.data;
            showAlert("pageAlert", `Opened ${created.type} account ${created.accountNumber} (${created.status}).`, "success");
            await loadAccounts();
        } catch (err) {
            showAlert("pageAlert", err.message || "Could not open account.", "error");
        } finally {
            setLoading(btn, false);
        }
    });
});
