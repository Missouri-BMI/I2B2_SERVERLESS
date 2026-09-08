const express = require("express");
const fetch = require("isomorphic-fetch");

const config = require("./config");

const staticDataRouter = express.Router();

process.env.NODE_TLS_REJECT_UNAUTHORIZED = 0;

staticDataRouter.route("/webClientConfig").get(async (req, res) => {
  const url = `${config.baseUrl}staticData/webClientConfig`;
  try {
    const response = await fetch(url, {
      method: "GET"
    });
    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.sendStatus(response.status);
    }
  } catch (e) {
    res.sendStatus(404);
  }
});

module.exports = staticDataRouter;
