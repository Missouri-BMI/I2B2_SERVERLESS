const express = require("express");
const fetch = require("isomorphic-fetch");
const config = require("./config");

const qepRouter = express.Router();

process.env.NODE_TLS_REJECT_UNAUTHORIZED = 0;

qepRouter.route("/queryResult").get(async (req, res) => {
  const {
    limit = 50,
    sortBy = "dateCreated.desc",
    skip = 0,
    afterVersion = -1,
    networkId,
    sortSiteBy = "site.desc",
  } = req.query;

  const headers = {
    Authorization: req.headers.authorization,
  };
  const defaultUrl = `${config.baseUrl}qep/queryResult?&limit=${limit}&sortBy=${sortBy}&skip=${skip}`;
  const url = networkId
    ? `${defaultUrl}&networkId=${networkId}&sortSiteBy=${sortSiteBy}`
    : defaultUrl;

  try {
    const response = await fetch(url, {
      headers,
      method: "GET",
    });
    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.sendStatus(404);
    }
  } catch (e) {
    res.sendStatus(404);
  }
});

qepRouter.route("/startQuery").post(async (req, res) => {
  const url = `${config.baseUrl}qep/startQuery`;
  const headers = {
    Authorization: req.headers.authorization,
  };
  const body = JSON.stringify(req.body);

  try {
    const response = await fetch(url, {
      headers,
      method: "POST",
      body,
    });
    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.sendStatus(response.status);
    }
  } catch (e) {
    res.sendStatus(400);
  }
});

qepRouter.route("/login").get(async (req, res) => {
  const { headers } = req;
  const url = `${config.baseUrl}qep/login`;

  try {
    const response = await fetch(url, {
      headers,
      method: "GET",
    });

    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.set(
        "WWW-Authenticate",
        'Basic realm="shrine-webclient-node01.catalyst.harvard.edu"'
      );
      res.status(401).send("Unauthorized");
    }
  } catch (e) {
    res.set(
      "WWW-Authenticate",
      'Basic realm="shrine-webclient-node01.catalyst.harvard.edu"'
    );
    res.status(401).send("Unauthorized");
  }
});

qepRouter.route("/changeQueryFav/:networkId").post(async (req, res) => {
  const url = `${config.baseUrl}qep/changeQueryFav/${req.params.networkId}`;
  const headers = {
    Authorization: req.headers.authorization,
  };
  const body = JSON.stringify(req.body);

  try {
    const response = await fetch(url, {
      headers,
      method: "POST",
      body,
    });
    if (response.ok === true) {
      res.sendStatus(response.status);
    } else {
      res.sendStatus(response.status);
    }
  } catch (e) {
    res.sendStatus(400);
  }
});

qepRouter.route("/renameQuery/:queryId").post(async (req, res) => {
  const url = `${config.baseUrl}qep/renameQuery/${req.params.queryId}`;

  const headers = {
    Authorization: req.headers.authorization,
  };
  const body = JSON.stringify(req.body);

  try {
    const response = await fetch(url, {
      headers,
      method: "POST",
      body,
    });
    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.sendStatus(response.status);
    }
  } catch (e) {
    res.sendStatus(400);
  }
});

// todo:  When server side work is done for SHRINE2020-290 this route hould be "/query/:queryId"
qepRouter.route("/query/:networkId").get(async (req, res) => {
  const url = `${config.baseUrl}qep/query/${req.params.networkId}`;
  const headers = {
    Authorization: req.headers.authorization,
  };

  try {
    const response = await fetch(url, {
      headers,
      method: "GET",
    });
    if (response.ok === true) {
      res.json(await response.json());
    } else {
      res.sendStatus(response.status);
    }
  } catch (e) {
    res.sendStatus(400);
  }
});

module.exports = qepRouter;
