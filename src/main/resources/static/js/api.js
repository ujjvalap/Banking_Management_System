(() => {
    let accessToken = null;
    let refreshPromise = null;

    function getCookie(name) {
        const prefix = name + "=";
        const parts = document.cookie ? document.cookie.split(";") : [];
        for (const raw of parts) {
            const trimmed = raw.trim();
            if (trimmed.startsWith(prefix)) {
                return decodeURIComponent(trimmed.substring(prefix.length));
            }
        }
        return null;
    }

    function setAccessToken(token) {
        accessToken = token || null;
    }

    function clearAccessToken() {
        accessToken = null;
    }

    async function refreshSession() {
        if (refreshPromise) {
            return refreshPromise;
        }
        refreshPromise = (async () => {
            const csrfToken = getCookie("XSRF-TOKEN");
            const headers = { "Content-Type": "application/json" };
            if (csrfToken) {
                headers["X-XSRF-TOKEN"] = csrfToken;
            }
            const res = await fetch("/api/auth/refresh", {
                method: "POST",
                credentials: "same-origin",
                headers,
                body: "{}"
            });
            if (!res.ok) {
                clearAccessToken();
                return false;
            }
            const payload = await res.json().catch(() => null);
            const newToken = payload?.data?.accessToken;
            if (newToken) {
                setAccessToken(newToken);
            }
            return true;
        })().finally(() => {
            refreshPromise = null;
        });
        return refreshPromise;
    }

    async function request(url, options = {}, retryOn401 = true) {
        const method = (options.method || "GET").toUpperCase();
        const headers = { ...(options.headers || {}) };

        if (options.body && !headers["Content-Type"]) {
            headers["Content-Type"] = "application/json";
        }
        if (accessToken && !headers["Authorization"]) {
            headers["Authorization"] = "Bearer " + accessToken;
        }
        if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
            const csrfToken = getCookie("XSRF-TOKEN");
            if (csrfToken && !headers["X-XSRF-TOKEN"]) {
                headers["X-XSRF-TOKEN"] = csrfToken;
            }
        }

        const response = await fetch(url, {
            ...options,
            method,
            headers,
            credentials: "same-origin"
        });

        if (response.status === 401 && retryOn401 && !url.startsWith("/api/auth/login") && !url.startsWith("/api/auth/refresh")) {
            const refreshed = await refreshSession();
            if (refreshed) {
                return request(url, options, false);
            }
            if (!window.location.pathname.startsWith("/auth/")) {
                window.location.assign("/auth/login?expired=true");
            }
        }

        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        let payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch {
                payload = { message: text };
            }
        }

        if (!response.ok) {
            const validationMsg = payload?.validationErrors
                ? Object.values(payload.validationErrors).join(" ")
                : null;
            const err = new Error(validationMsg || payload?.message || "Request failed with status " + response.status);
            err.status = response.status;
            err.payload = payload;
            throw err;
        }

        return payload;
    }

    function showAlert(idOrElement, message, type = "error") {
        const el = typeof idOrElement === "string" ? document.getElementById(idOrElement) : idOrElement;
        if (!el) return;
        el.textContent = message;
        el.className = "alert " + (type === "success" ? "alert-success" : type === "info" ? "alert-info" : "alert-error");
        el.classList.remove("hidden");
    }

    function clearAlert(idOrElement) {
        const el = typeof idOrElement === "string" ? document.getElementById(idOrElement) : idOrElement;
        if (!el) return;
        el.textContent = "";
        el.classList.add("hidden");
    }

    function setLoading(button, isLoading, loadingText = "Processing...") {
        if (!button) return;
        if (isLoading) {
            button.dataset.originalText = button.textContent;
            button.textContent = loadingText;
            button.disabled = true;
        } else {
            button.textContent = button.dataset.originalText || button.textContent;
            button.disabled = false;
        }
    }

    function formatCurrency(amount) {
        const num = Number(amount || 0);
        return "₹" + num.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function formatDateTime(value) {
        if (!value) return "-";
        const normalized = value.endsWith("Z") || value.includes("+") ? value : value + "Z";
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) return value;
        return date.toLocaleString("en-IN", {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    async function logout() {
        try {
            await request("/api/auth/logout", { method: "POST" }, false);
        } catch {
            // Clear local token regardless.
        } finally {
            clearAccessToken();
            window.location.assign("/auth/login?logout=true");
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-logout-btn]").forEach((btn) => {
            btn.addEventListener("click", (event) => {
                event.preventDefault();
                logout();
            });
        });

        document.querySelectorAll("[data-toggle-password]").forEach((btn) => {
            btn.addEventListener("click", () => {
                const input = btn.closest(".password-wrap")?.querySelector("input");
                if (!input) return;
                const isPassword = input.type === "password";
                input.type = isPassword ? "text" : "password";
                btn.textContent = isPassword ? "Hide" : "Show";
            });
        });
    });

    window.BankApi = {
        get: (url) => request(url, { method: "GET" }),
        post: (url, body) => request(url, { method: "POST", body: JSON.stringify(body ?? {}) }),
        put: (url, body) => request(url, { method: "PUT", body: JSON.stringify(body ?? {}) }),
        request,
        setAccessToken,
        clearAccessToken,
        showAlert,
        clearAlert,
        setLoading,
        formatCurrency,
        formatDateTime,
        escapeHtml,
        logout
    };
})();
