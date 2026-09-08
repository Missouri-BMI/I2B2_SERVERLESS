import fetch from "isomorphic-fetch";

import auth from "./auth";
import { getCookie } from "utilities";

export const secureFetch = async (url, config) => {
  try {

    if (getCookie("isSsoMode") === "true") {
      if (!config) {
        config = {}
      }
      config.mode = 'no-cors';
    }

    let response = null;
    try {
      response = await fetch(url, auth.getFetchConfig(config));
    }
    catch (e) {
      // oh well
    }

    // TODO: research, looks like response.status seems to never be populated with 302,
    // and instead with 0
    if (  getCookie("isSsoMode") === "true" &&
        (!response || (!response.ok && response.status == "0" ))
      ) {
      location = "/shrine-api/shrine-webclient";
    }

    if (response.ok) {
      const text = await response.text();
      const shouldBeParsedToJson = !!text.length && text.toUpperCase() !== "OK";
      if (shouldBeParsedToJson) {
        const data = JSON.parse(text);
        return { ok: true, data };
      }

      return { ok: true, data: null };
    }
    return response;
  } catch (e) {
    return e;
  }
};


export const secureBlobFetch = async (url, config) => {
  try {
    const response = await fetch(url, auth.getFetchConfig(config));
    if (response.ok) {
      const responseText = await response.text();
      const responseBlob = new Blob([responseText], {type: 'text/csv'});
      return { ok: true, blob: responseBlob };
    }
    return response;
  } catch (e) {
    return e;
  }
};


