const express = require("express");
const debug = require("debug")("routes:ontology");
const fetch = require("isomorphic-fetch");

const config = require("./config");
const ontology = require("./mock-data/ontologyData");

const ontologyRouter = express.Router();

process.env.NODE_TLS_REJECT_UNAUTHORIZED = 0;

ontologyRouter.route("/mock").get((req, res) => {
  setTimeout(() => res.json(ontology), 2000);
});

ontologyRouter.route("/root").get(async (req, res) => {
  const headers = {
    Authorization: req.headers.authorization
  };
  const url = `${config.baseUrl}ontology/root`;
  try {
    const response = await fetch(url, {
      headers,
      method: "GET"
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

ontologyRouter.route("/filterOptions").get(async (req, res) => {
  const headers = {
    Authorization: req.headers.authorization
  };
  const url = `${config.baseUrl}ontology/filterOptions`;
  try {
    const response = await fetch(url, {
      headers,
      method: "GET"
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

ontologyRouter.route("/children").post(async (req, res) => {
  const { path } = req.body;
  const headers = {
    Authorization: req.headers.authorization,
    "Content-Type": "application/json"
  };
  const method = "POST";
  const body = JSON.stringify({ path });
  const url = `${config.baseUrl}ontology/children`; // const url = `${config.baseUrl}ontology/children`;
  try {
    const response = await fetch(url, {
      headers,
      method,
      body
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

ontologyRouter.route("/conceptInfo").post(async (req, res) => {
  const { path } = req.body;
  const headers = {
    Authorization: req.headers.authorization,
    "Content-Type": "application/json"
  };
  const method = "POST";
  const body = JSON.stringify({ path });
  const url = `${config.baseUrl}ontology/conceptInfo`;
  try {
    const response = await fetch(url, {
      headers,
      method,
      body
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

ontologyRouter.route("/suggest").post(async (req, res) => {
  const { suggestString } = req.body;
  const headers = {
    Authorization: req.headers.authorization,
    "Content-Type": "application/json"
  };
  const method = "POST";
  const url = `${config.baseUrl}ontology/suggest`;
  try {
    const response = await fetch(url, {
      headers,
      method,
      body: JSON.stringify({ suggestString })
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

ontologyRouter.route("/search").post(async (req, res) => {
  const { searchString, previousSearchMetadata = null, filterData } = req.body;
  const headers = {
    Authorization: req.headers.authorization,
    "Content-Type": "application/json"
  };
  const method = "POST";
  const url = `${config.baseUrl}ontology/search`;
  const body = previousSearchMetadata
    ? JSON.stringify({ searchString, filterData, previousSearchMetadata })
    : JSON.stringify({ searchString, filterData });
  try {
    const response = await fetch(url, {
      headers,
      method,
      body
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

ontologyRouter.route("/labDetails").post(async (req, res) => {
  const { path } = req.body;

  const headers = {
    Authorization: req.headers.authorization,
    "Content-Type": "application/json"
  };
  const method = "POST";
  const url = `${config.baseUrl}ontology/labDetails`;
  try {
    const response = await fetch(url, {
      headers,
      method,
      body: JSON.stringify({ path })
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

module.exports = ontologyRouter;
