document.addEventListener("DOMContentLoaded", () => {
    const { post, get, setAccessToken, showAlert, clearAlert, setLoading } = window.BankApi;

    const params = new URLSearchParams(window.location.search);
    const alertId = "pageAlert";
    if (params.get("logout") === "true") {
        showAlert(alertId, "You have been signed out securely.", "success");
    } else if (params.get("expired") === "true") {
        showAlert(alertId, "Your session expired. Please sign in again.", "info");
    } else if (params.get("passwordReset") === "true") {
        showAlert(alertId, "Your password was reset. Sign in with your new password.", "success");
    } else if (params.get("verified") === "true") {
        showAlert(alertId, "Email verified successfully. You can now sign in.", "success");
    }

    const loginForm = document.getElementById("loginForm");
    loginForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearAlert(alertId);
        const btn = loginForm.querySelector('button[type="submit"]');
        setLoading(btn, true, "Signing in...");

        const form = new FormData(loginForm);
        try {
            const response = await post("/api/auth/login", {
                email: form.get("email"),
                password: form.get("password")
            });
            const auth = response?.data;
            if (auth?.accessToken) {
                setAccessToken(auth.accessToken);
            }
            const role = auth?.user?.role;
            window.location.assign(role === "ADMIN" ? "/admin/dashboard" : "/customer/dashboard");
        } catch (err) {
            showAlert(alertId, err.message || "Invalid email or password.", "error");
        } finally {
            setLoading(btn, false);
        }
    });

    const registerForm = document.getElementById("registerForm");
    registerForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearAlert(alertId);
        const btn = registerForm.querySelector('button[type="submit"]');
        setLoading(btn, true, "Creating account...");

        const form = new FormData(registerForm);
        try {
            const response = await post("/api/auth/register", {
                fullName: form.get("fullName"),
                email: form.get("email"),
                password: form.get("password"),
                phone: form.get("phone"),
                address: form.get("address"),
                pan: form.get("pan")
            });
            const auth = response?.data;
            if (auth?.accessToken) {
                setAccessToken(auth.accessToken);
                window.location.assign("/customer/dashboard");
            } else {
                showAlert(alertId, auth?.message || "Account created. Please verify your email before logging in.", "success");
                registerForm.reset();
            }
        } catch (err) {
            showAlert(alertId, err.message || "Unable to complete registration.", "error");
        } finally {
            setLoading(btn, false);
        }
    });

    const forgotForm = document.getElementById("forgotPasswordForm");
    forgotForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearAlert(alertId);
        const btn = forgotForm.querySelector('button[type="submit"]');
        setLoading(btn, true, "Sending...");

        const form = new FormData(forgotForm);
        try {
            const response = await post("/api/auth/forgot-password", { email: form.get("email") });
            showAlert(alertId, response?.message || "If an account matches that email, a reset link has been sent.", "success");
        } catch (err) {
            showAlert(alertId, err.message || "Unable to process password reset request.", "error");
        } finally {
            setLoading(btn, false);
        }
    });

    const resetForm = document.getElementById("resetPasswordForm");
    if (resetForm) {
        const hashToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
        const queryToken = params.get("token");
        const token = hashToken || queryToken;

        resetForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert(alertId);
            const form = new FormData(resetForm);
            const newPassword = form.get("newPassword");
            const confirmPassword = form.get("confirmPassword");

            if (!token) {
                showAlert(alertId, "Reset link is missing or invalid.", "error");
                return;
            }
            if (newPassword !== confirmPassword) {
                showAlert(alertId, "Passwords do not match.", "error");
                return;
            }

            const btn = resetForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Resetting...");
            try {
                await post("/api/auth/reset-password", { token, newPassword });
                window.location.assign("/auth/login?passwordReset=true");
            } catch (err) {
                showAlert(alertId, err.message || "Reset link is invalid or expired.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }

    const verifyPage = document.getElementById("verifyEmailSection");
    if (verifyPage) {
        const hashToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
        const token = hashToken || params.get("token");
        if (token) {
            showAlert(alertId, "Verifying your email address...", "info");
            get("/api/auth/verify-email?token=" + encodeURIComponent(token))
                .then(() => showAlert(alertId, "Email verified successfully. You can now sign in.", "success"))
                .catch((err) => showAlert(alertId, err.message || "Invalid or expired verification link.", "error"));
        }

        const resendForm = document.getElementById("resendVerificationForm");
        resendForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearAlert(alertId);
            const btn = resendForm.querySelector('button[type="submit"]');
            setLoading(btn, true, "Sending...");
            try {
                const form = new FormData(resendForm);
                const res = await post("/api/auth/resend-verification", { email: form.get("email") });
                showAlert(alertId, res?.message || "Verification link sent if required.", "success");
            } catch (err) {
                showAlert(alertId, err.message || "Email delivery is currently unavailable.", "error");
            } finally {
                setLoading(btn, false);
            }
        });
    }
});
