export const SHRINE_AUTHENTICATION_TOKEN_ID = "SHRINE_AUTHENTICATION_TOKEN";
export const SHRINE_AUTHENTICATION_TOKEN_TIMEOUT =
  "SHRINE_AUTHENTICATION_TOKEN_TIMEOUT";

class Auth {
  storage = window.sessionStorage;

  set token(data) {
    if (!this.storage.getItem(SHRINE_AUTHENTICATION_TOKEN_ID)) {
      this.storage.setItem(
        SHRINE_AUTHENTICATION_TOKEN_ID,
        btoa(JSON.stringify(data))
      );
    } else {
      throw Error("Session authorization token cannot be changed");
    }

    if (data && !this.storage.getItem(SHRINE_AUTHENTICATION_TOKEN_TIMEOUT)) {
      this.storage.setItem(
        SHRINE_AUTHENTICATION_TOKEN_TIMEOUT,
        data.sessionTimeoutMs
      );
    } else {
      throw Error("Session authorization token timeout cannot be changed");
    }
  }

  get token() {
    return this.storage.getItem(SHRINE_AUTHENTICATION_TOKEN_ID);
  }

  get timeoutMs() {
    return this.storage.getItem(SHRINE_AUTHENTICATION_TOKEN_TIMEOUT);
  }

  get tokenAuthorizationObject() {
    return JSON.parse(atob(this.token));
  }

  get isAuthenticated() {
    return this.token !== null;
  }

  clear = () => {
    this.storage.removeItem(SHRINE_AUTHENTICATION_TOKEN_ID);
    this.storage.removeItem(SHRINE_AUTHENTICATION_TOKEN_TIMEOUT);
  };

  get Authorization() {
    return `Bearer ${this.token}`;
  }

  getFetchConfig = (
    config = {
      method: "GET",
      headers: {}
    }
  ) => ({
    ...config,
    headers: {
      ...config.headers,
      "X-Requested-With": "XMLHttpRequest",
      Authorization: this.Authorization
    }
  });
}

export default new Auth();
