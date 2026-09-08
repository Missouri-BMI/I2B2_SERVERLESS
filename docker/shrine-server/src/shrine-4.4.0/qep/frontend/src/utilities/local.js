import moment from 'moment';
import crypto from "crypto-js";

class Local {
  storage = window.localStorage;

  privateKey = null;

  set localKey(key) {
    if (this.privateKey === null) {
      this.privateKey = key;
    } else {
      throw Error("The session key can only be set once per session");
    }
  }

  get localKey() {
    if (this.privateKey !== null) {
      return this.privateKey;
    }
    throw Error("You are trying to access a null session key.");
  }

  clearLocalKey = () => {
    this.privateKey = null;
  };

  hasLocalKey = () => {
    return this.privateKey !== null;
  };

  hasUserConfig = () => {
    return this.hasLocalKey() && !!this.getUserConfig(this.localKey);
  };

  getUserConfig = () => {
    const data = this.storage.getItem(this.localKey);
    return data ? JSON.parse(data) : null;
  };

  setUserConfig = (data) => {
    this.storage.setItem(this.localKey, JSON.stringify(data));
  };

  setTOUAccepted = (termsOfUseText) => {
    const config = this.hasUserConfig() ? this.getUserConfig() : {};
    let expireDate = moment().add(30,'d').toDate().setHours(0, 0, 0, 0);
    let touHashCode = termsOfUseText !== null ? crypto.SHA256(termsOfUseText).toString() : null;

    config.termsOfUse = {
      expiryDate: expireDate,
      touHashCode: touHashCode
    };

    this.storage.setItem(this.localKey, JSON.stringify(config));
  };

  isTOUAccepted = (termsOfUseText) => {
    const config = this.hasUserConfig() ? this.getUserConfig() : {};
    let isAccepted = false;

    if(config.termsOfUse !== undefined && config.termsOfUse.expiryDate !== undefined && termsOfUseText !== null){
      let touHashCode = crypto.SHA256(termsOfUseText).toString();
      let existingTouHashCode = config.termsOfUse.touHashCode;

      let currentDate = moment();
      let expireDate = moment(config.termsOfUse.expiryDate);
       if(currentDate.isBefore(expireDate) &&  touHashCode === existingTouHashCode) {
          isAccepted = true;
       }
    }

    return isAccepted;
  };
}

export default new Local();
