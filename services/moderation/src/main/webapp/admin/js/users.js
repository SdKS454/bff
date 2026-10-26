(function () {
    "use strict";

    const TOKEN_KEY = "accessToken";

    function getToken() { return sessionStorage.getItem(TOKEN_KEY); }
    function saveToken(token) { sessionStorage.setItem(TOKEN_KEY, token); }
    function clearToken() { sessionStorage.removeItem(TOKEN_KEY); }
    function goToLogin() { window.location.href = LOGIN_URL; }

    function parseJwt(token) {
        try {
            const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
            return JSON.parse(atob(part));
        } catch (e) {
            return null;
        }
    }

    async function request(url, options = {}) {
        let response;
        try {
            response = await fetch(url, {
                ...options,
                headers: {
                    "Accept": "application/json",
                    ...(options.body ? { "Content-Type": "application/json" } : {}),
                    "Authorization": "Bearer " + getToken()
                }
            });
        } catch (e) {
            throw { status: 0, message: "Нет связи с сервером" };
        }

        let data = null;
        try { data = await response.json(); } catch (e) { }

        if (!response.ok) {
            throw { status: response.status, message: data && data.error };
        }
        return data;
    }

    const messageEl = document.getElementById("message");

    function showMessage(text, type) {
        messageEl.textContent = text;
        messageEl.className = "message" + (type ? " message--" + type : "");
        messageEl.hidden = false;
    }

    function hideMessage() {
        messageEl.hidden = true;
        messageEl.textContent = "";
    }

    function errorToText(err) {
        if (!err || typeof err.status === "undefined") return "Нет связи с сервером";
        const status = err.status;
        if (status === 0) return "Нет связи с сервером";
        if (status === 400 || status === 404 || status === 409) return err.message || "Ошибка запроса";
        if (status === 403) return "Недостаточно прав";
        if (status === 500 || status === 502 || status === 503) return "Сервис временно недоступен";
        if (status === 401) return "Требуется авторизация";
        return err.message || "Ошибка";
    }

    function handleError(err, inlineTarget) {
        if (err && err.status === 401) {
            clearToken();
            goToLogin();
            return;
        }
        const text = errorToText(err);
        if (inlineTarget) {
            inlineTarget.textContent = text;
            inlineTarget.className = "message message--inline message--error";
            inlineTarget.hidden = false;
        } else {
            showMessage(text, "error");
        }
    }

    function formatDuration(seconds) {
        if (seconds === null || seconds === undefined) return "Навсегда";
        if (seconds <= 172800) {
            const h = seconds / 3600;
            const word = (h % 10 >= 2 && h % 10 <= 4 && (h < 12 || h > 14)) ? "часа" : "часов";
            return h + " " + word;
        }
        const d = seconds / 86400;
        const word = d % 10 === 1 && d !== 11 ? "день"
            : (d % 10 >= 2 && d % 10 <= 4 && (d < 12 || d > 14)) ? "дня" : "дней";
        return d + " " + word;
    }

    function formatDate(value) {
        if (!value) return "—";
        const date = new Date(value);
        if (isNaN(date.getTime())) return "—";
        return date.toLocaleString("ru-RU");
    }

    const state = {
        users: [],
        blocks: {},
        reasons: [],
        filter: "all",
        loading: false,
        reasonsLoaded: false
    };

    async function loadUsers() {
        return request(USER_URL + "/api/users");
    }

    async function loadBlocksPage(offset) {
        return request(USER_URL + "/api/users/blocked?limit=200&offset=" + offset);
    }

    async function loadAllBlocks() {
        const result = [];
        let offset = 0;
        for (let guard = 0; guard < 100; guard++) {
            const page = await loadBlocksPage(offset);
            if (!Array.isArray(page) || page.length === 0) break;
            result.push(...page);
            if (page.length < 200) break;
            offset += 200;
        }
        return result;
    }

    async function loadReasons() {
        return request(USER_URL + "/api/users/reasons");
    }

    async function loadAll(keepMessage) {
        if (state.loading) return;
        state.loading = true;
        if (!keepMessage) hideMessage();
        setTableState("Загрузка…");

        const reasonsPromise = loadReasons().then(
            (data) => ({ ok: true, data }),
            (err) => ({ ok: false, err })
        );

        try {
            const [users, blocks] = await Promise.all([loadUsers(), loadAllBlocks()]);
            state.users = Array.isArray(users) ? users : [];
            state.blocks = {};
            if (Array.isArray(blocks)) {
                for (const b of blocks) {
                    if (b && b.userId) state.blocks[String(b.userId).toLowerCase()] = b;
                }
            }

            const reasonsResult = await reasonsPromise;
            if (reasonsResult.ok) {
                state.reasons = Array.isArray(reasonsResult.data) ? reasonsResult.data : [];
                state.reasonsLoaded = true;
            } else {
                state.reasons = [];
                state.reasonsLoaded = false;
                if (reasonsResult.err && reasonsResult.err.status === 401) {
                    handleError(reasonsResult.err);
                    return;
                }
                showMessage("Не удалось загрузить справочник причин: " + errorToText(reasonsResult.err), "error");
            }

            document.getElementById("content").hidden = false;
            render();
        } catch (err) {
            document.getElementById("content").hidden = false;
            state.users = [];
            state.blocks = {};
            render();
            handleError(err);
        } finally {
            state.loading = false;
        }
    }

    const tbody = document.getElementById("users-tbody");

    function setTableState(text) {
        tbody.innerHTML = "";
        const tr = document.createElement("tr");
        tr.className = "table-state";
        const td = document.createElement("td");
        td.colSpan = 7;
        td.textContent = text;
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    function getBlockForUser(userId) {
        if (!userId) return null;
        return state.blocks[String(userId).toLowerCase()] || null;
    }

    function rowMatchesFilter(user) {
        const blocked = !!getBlockForUser(user.id);
        if (state.filter === "active") return !blocked;
        if (state.filter === "blocked") return blocked;
        return true;
    }

    function textCell(text) {
        const td = document.createElement("td");
        if (text === null || text === undefined || text === "") {
            const span = document.createElement("span");
            span.className = "text-muted";
            span.textContent = "—";
            td.appendChild(span);
        } else {
            td.textContent = text;
        }
        return td;
    }

    function buildRow(user) {
        const block = getBlockForUser(user.id);
        const blocked = !!block;
        const tr = document.createElement("tr");

        const loginCell = document.createElement("td");
        const strong = document.createElement("strong");
        strong.textContent = user.login || "—";
        loginCell.appendChild(strong);
        tr.appendChild(loginCell);

        tr.appendChild(textCell(user.fullName));

        const statusCell = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = "badge " + (blocked ? "badge--blocked" : "badge--active");
        badge.textContent = blocked ? "Заблокирован" : "Активен";
        statusCell.appendChild(badge);
        tr.appendChild(statusCell);

        tr.appendChild(textCell(blocked ? block.reason : null));
        tr.appendChild(textCell(blocked && block.blockedAt ? formatDate(block.blockedAt) : null));

        let untilText = null;
        if (blocked) {
            untilText = block.blockedUntil ? formatDate(block.blockedUntil) : "Навсегда";
        }
        tr.appendChild(textCell(untilText));

        const actionCell = document.createElement("td");
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "btn btn--sm " + (blocked ? "btn--ghost" : "btn--danger");
        btn.textContent = blocked ? "Разблокировать" : "Заблокировать";
        if (blocked) {
            btn.addEventListener("click", () => openUnbanModal(user));
        } else {
            btn.addEventListener("click", () => openBanModal(user));
            if (!state.reasonsLoaded || state.reasons.length === 0) {
                btn.disabled = true;
                btn.title = "Справочник причин недоступен";
            }
        }
        actionCell.appendChild(btn);
        tr.appendChild(actionCell);

        return tr;
    }

    function render() {
        const visible = state.users.filter(rowMatchesFilter);
        if (visible.length === 0) {
            setTableState("Пользователей не найдено");
            return;
        }
        tbody.innerHTML = "";
        const fragment = document.createDocumentFragment();
        for (const user of visible) fragment.appendChild(buildRow(user));
        tbody.appendChild(fragment);
    }

    const banModal = document.getElementById("ban-modal");
    const banUserEl = document.getElementById("ban-modal-user");
    const banReasonSelect = document.getElementById("ban-reason");
    const banDurationEl = document.getElementById("ban-duration");
    const banConfirmBtn = document.getElementById("ban-confirm");
    const banErrorEl = document.getElementById("ban-error");
    let banTargetUser = null;

    function openBanModal(user) {
        banTargetUser = user;
        banErrorEl.hidden = true;
        banUserEl.textContent = (user.login || "") + (user.fullName ? " — " + user.fullName : "");
        banReasonSelect.innerHTML = '<option value="" selected disabled>Выберите причину</option>';
        for (const reason of state.reasons) {
            const opt = document.createElement("option");
            opt.value = reason.reasonType;
            opt.textContent = reason.description;
            banReasonSelect.appendChild(opt);
        }
        banDurationEl.textContent = "";
        banConfirmBtn.disabled = true;
        banModal.hidden = false;
    }

    function closeBanModal() {
        banModal.hidden = true;
        banTargetUser = null;
    }

    function selectedReason() {
        const val = banReasonSelect.value;
        return val ? state.reasons.find(r => r.reasonType === val) : null;
    }

    banReasonSelect.addEventListener("change", () => {
        const reason = selectedReason();
        banConfirmBtn.disabled = !reason;
        banDurationEl.textContent = reason ? "Срок: " + formatDuration(reason.durationSeconds) : "";
    });

    document.getElementById("ban-cancel").addEventListener("click", closeBanModal);
    banModal.querySelector(".modal__overlay").addEventListener("click", closeBanModal);

    banConfirmBtn.addEventListener("click", async () => {
        const reason = selectedReason();
        if (!reason || !banTargetUser) return;
        banErrorEl.hidden = true;
        banConfirmBtn.disabled = true;
        try {
            const response = await request(
                USER_URL + "/api/users/" + encodeURIComponent(banTargetUser.id) + "/ban",
                { method: "POST", body: JSON.stringify({ reason: reason.reasonType }) }
            );
            closeBanModal();
            if (response && response.reasonType && response.reasonType !== reason.reasonType) {
                showMessage("Пользователь уже заблокирован: " + (response.reason || response.reasonType), "");
            } else {
                showMessage("Пользователь заблокирован", "success");
            }
            await loadAll(true);
        } catch (err) {
            if (err && err.status === 401) {
                clearToken();
                goToLogin();
                return;
            }
            handleError(err, banErrorEl);
            banConfirmBtn.disabled = false;
        }
    });

    const unbanModal = document.getElementById("unban-modal");
    const unbanTextEl = document.getElementById("unban-modal-text");
    const unbanConfirmBtn = document.getElementById("unban-confirm");
    const unbanErrorEl = document.getElementById("unban-error");
    let unbanTargetUser = null;

    function openUnbanModal(user) {
        unbanTargetUser = user;
        unbanErrorEl.hidden = true;
        unbanTextEl.textContent = "Разблокировать пользователя " + (user.login || "") + "?";
        unbanConfirmBtn.disabled = false;
        unbanModal.hidden = false;
    }

    function closeUnbanModal() {
        unbanModal.hidden = true;
        unbanTargetUser = null;
    }

    document.getElementById("unban-cancel").addEventListener("click", closeUnbanModal);
    unbanModal.querySelector(".modal__overlay").addEventListener("click", closeUnbanModal);

    unbanConfirmBtn.addEventListener("click", async () => {
        if (!unbanTargetUser) return;
        unbanErrorEl.hidden = true;
        unbanConfirmBtn.disabled = true;
        try {
            await request(
                USER_URL + "/api/users/" + encodeURIComponent(unbanTargetUser.id) + "/unban",
                { method: "POST" }
            );
            closeUnbanModal();
            showMessage("Блокировка снята", "success");
            await loadAll(true);
        } catch (err) {
            if (err && err.status === 401) {
                clearToken();
                goToLogin();
                return;
            }
            handleError(err, unbanErrorEl);
            unbanConfirmBtn.disabled = false;
        }
    });

    document.addEventListener("keydown", (e) => {
        if (e.key !== "Escape") return;
        if (!banModal.hidden) closeBanModal();
        else if (!unbanModal.hidden) closeUnbanModal();
    });

    document.querySelectorAll(".btn--filter").forEach(btn => {
        btn.addEventListener("click", () => {
            state.filter = btn.dataset.filter;
            document.querySelectorAll(".btn--filter").forEach(b => b.classList.toggle("is-active", b === btn));
            render();
        });
    });

    document.getElementById("refresh-btn").addEventListener("click", () => loadAll());
    document.getElementById("logout-btn").addEventListener("click", async () => {
        const button = document.getElementById("logout-btn");
        button.disabled = true;
        try {
            try { await request(USER_URL + "/api/users/logout", { method: "POST" }); }
            catch (err) { if (!err || err.status !== 401) throw err; }
            clearToken();
            goToLogin();
        } catch (err) {
            showMessage("Не удалось отозвать токен: " + errorToText(err) + ". Повторите выход.", "error");
        } finally { button.disabled = false; }
    });

    function init() {
        const hashParams = new URLSearchParams(location.hash.slice(1));
        const tokenFromUrl = hashParams.get("accessToken");
        if (tokenFromUrl) {
            saveToken(tokenFromUrl);
            history.replaceState(null, "", location.pathname);
        }

        const token = getToken();
        if (!token) {
            goToLogin();
            return;
        }

        const payload = parseJwt(token);
        if (!payload) {
            clearToken();
            goToLogin();
            return;
        }
        if (payload.exp !== undefined && Number(payload.exp) < Date.now() / 1000) {
            clearToken();
            goToLogin();
            return;
        }
        if (payload.role !== "ADMIN") {
            showMessage("Недостаточно прав", "error");
            return;
        }

        document.getElementById("content").hidden = false;
        loadAll();
    }

    init();
})();
