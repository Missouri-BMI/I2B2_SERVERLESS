class SessionStorage {
  storage = window.sessionStorage;

  privateKey = null;

  set sessionKey(key) {
    if (this.privateKey === null) {
      this.privateKey = key;
    } else {
      throw Error("The session key can only be set once per session");
    }
  }

  get sessionKey() {
    if (this.privateKey !== null) {
      return this.privateKey;
    }
    throw Error("You are trying to access a null session key.");
  }

  clearSessionKey = () => {
    this.privateKey = null;
  };

  hasSessionKey = () => {
    return this.privateKey !== null;
  };

  hasUserConfig = () => {
    return this.hasSessionKey() && !!this.getUserConfig(this.sessionKey);
  };

  getUserConfig = () => {
    const data = this.storage.getItem(this.sessionKey);
    return data ? JSON.parse(data) : null;
  };

  setUserConfig = (data) => {
    this.storage.setItem(this.sessionKey, JSON.stringify(data));
  };

  clearUserConfig = () => {
    this.storage.removeItem(this.sessionKey);
  };
}

export default new SessionStorage();
