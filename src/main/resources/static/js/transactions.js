document.addEventListener("DOMContentLoaded", async () => {
    const { get, post, put, showAlert, clearAlert, setLoading, formatCurrency, escapeHtml } = window.BankApi;

    async function populateAccountSelect(selectId, activeOnly = true) {
        const select = document.getElementById(selectId);
        if (!select) return;

        const preselect = new URLSearchParams(window.location.search).get("accountId");
        try {
            const res = await get("/api/accounts");
            const accounts = (res?.data || []).filter((acc) => !activeOnly || acc.status === "ACTIVE");
            if (accounts.length === 0) {
                select.innerHTML = '<option value="">No active accounts available</option>';
                return;
            }
            select.innerHTML = '<option value="">Choose an account</option>' + accounts.map((acc) => `
                <option value="${escapeHtml(acc.id)}" ${preselect === String(acc.id) ? "selected" : ""}>
                    ${escapeHtml(acc.accountNumber)} (${escapeHtml(acc.type)} — ${formatCurrency(acc.balance)})
                </option>
            `).join("");
        } catch (err) {
            showAlert("pageAlert", err.message || "Failed to load accounts.", "error");
        }
    }

    const depositForm = document.getElementById("depositForm");
    if (depositForm) {
        await populateAccountSelect("depositAccountId", true);
        depositForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert("pageAlert");
            const form = new FormData(depositForm);
            const accountId = form.get("accountId");
            if (!accountId) {
                showAlert("pageAlert", "Please select an account.", "error");
                return;
            }
            const btn = depositForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Depositing...");
            try {
                const res = await post(`/api/accounts/${encodeURIComponent(accountId)}/deposit`, {
                    amount: Number(form.get("amount")),
                    description: form.get("description") || null
                });
                const updated = res?.data;
                showAlert("pageAlert", `Deposit successful! New balance for ${updated.accountNumber}: ${formatCurrency(updated.balance)}`, "success");
                depositForm.reset();
                await populateAccountSelect("depositAccountId", true);
            } catch (err) {
                showAlert("pageAlert", err.message || "Deposit failed.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }

    const withdrawForm = document.getElementById("withdrawForm");
    if (withdrawForm) {
        await populateAccountSelect("withdrawAccountId", true);
        withdrawForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert("pageAlert");
            const form = new FormData(withdrawForm);
            const accountId = form.get("accountId");
            if (!accountId) {
                showAlert("pageAlert", "Please select an account.", "error");
                return;
            }
            const btn = withdrawForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Withdrawing...");
            try {
                const res = await post(`/api/accounts/${encodeURIComponent(accountId)}/withdraw`, {
                    amount: Number(form.get("amount")),
                    description: form.get("description") || null
                });
                const updated = res?.data;
                showAlert("pageAlert", `Withdrawal successful! Remaining balance for ${updated.accountNumber}: ${formatCurrency(updated.balance)}`, "success");
                withdrawForm.reset();
                await populateAccountSelect("withdrawAccountId", true);
            } catch (err) {
                showAlert("pageAlert", err.message || "Withdrawal failed.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }

    const transferForm = document.getElementById("transferForm");
    if (transferForm) {
        await populateAccountSelect("fromAccountId", true);
        transferForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert("pageAlert");
            const form = new FormData(transferForm);
            const fromAccountId = form.get("fromAccountId");
            if (!fromAccountId) {
                showAlert("pageAlert", "Please select a source account.", "error");
                return;
            }
            const btn = transferForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Transferring...");
            try {
                const res = await post("/api/transfers", {
                    fromAccountId: Number(fromAccountId),
                    toAccountNumber: form.get("toAccountNumber"),
                    amount: Number(form.get("amount")),
                    description: form.get("description") || null
                });
                const tx = res?.data;
                showAlert("pageAlert", `Transfer completed (Ref: ${tx.referenceId}). ${tx.description}`, "success");
                transferForm.reset();
                await populateAccountSelect("fromAccountId", true);
            } catch (err) {
                showAlert("pageAlert", err.message || "Transfer failed.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }

    const profilePage = document.getElementById("customerProfilePage");
    if (profilePage) {
        async function loadProfile() {
            try {
                const res = await get("/api/customers/me");
                const p = res?.data;
                document.getElementById("profileFullName").textContent = p.fullName;
                document.getElementById("profileEmail").textContent = p.email;
                document.getElementById("profilePhone").textContent = p.phone;
                document.getElementById("profileAddress").textContent = p.address;
                document.getElementById("profilePan").textContent = p.pan;
                document.getElementById("editFullName").value = p.fullName;
                document.getElementById("editPhone").value = p.phone;
                document.getElementById("editAddress").value = p.address;
            } catch (err) {
                showAlert("pageAlert", err.message || "Unable to load customer profile.", "error");
            }
        }
        await loadProfile();

        const updateProfileForm = document.getElementById("updateProfileForm");
        updateProfileForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert("pageAlert");
            const btn = updateProfileForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Saving...");
            const form = new FormData(updateProfileForm);
            try {
                await put("/api/customers/me", {
                    fullName: form.get("fullName"),
                    phone: form.get("phone"),
                    address: form.get("address")
                });
                showAlert("pageAlert", "Profile updated successfully.", "success");
                await loadProfile();
            } catch (err) {
                showAlert("pageAlert", err.message || "Failed to update profile.", "error");
            } finally {
                setLoading(btn, false);
            }
        });

        const changePasswordForm = document.getElementById("changePasswordForm");
        changePasswordForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert("pageAlert");
            const form = new FormData(changePasswordForm);
            const newPassword = form.get("newPassword");
            if (newPassword !== form.get("confirmPassword")) {
                showAlert("pageAlert", "New password and confirmation do not match.", "error");
                return;
            }
            const btn = changePasswordForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Updating...");
            try {
                await post("/api/auth/change-password", {
                    currentPassword: form.get("currentPassword"),
                    newPassword
                });
                window.BankApi.clearAccessToken();
                window.location.assign("/auth/login?passwordReset=true");
            } catch (err) {
                showAlert("pageAlert", err.message || "Failed to change password.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }
});
