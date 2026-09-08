const express = require("express");
const chalk = require("chalk");
const debug = require("debug")("app");
const morgan = require("morgan");
const path = require("path");
const bodyParser = require("body-parser");
const webpack = require("webpack");
const webpackDevMiddleware = require("webpack-dev-middleware");
const webpackHotMiddleware = require("webpack-hot-middleware");

const qepRouter = require("./routes/qep");
const ontologyRouter = require("./routes/ontology");
const staticDataRouter = require("./routes/staticData");
const webpackConfig = require("../webpack.dev.config");

const compiler = webpack(webpackConfig);
const app = express();
const port = process.env.PORT || 3000;

// middleware
app.use(
  webpackDevMiddleware(compiler, {
    hot: true,
    stats: {
      colors: true,
    },
  })
);
app.use(webpackHotMiddleware(compiler));
app.use(morgan("tiny"));
app.use(bodyParser.json());
app.use(
  bodyParser.urlencoded({
    extended: true,
  })
);
app.use(express.static(path.join(__dirname, "/webapp")));
app.use("/shrine-api/qep", qepRouter);
app.use("/shrine-api/ontology", ontologyRouter);
app.use("/shrine-api/staticData", staticDataRouter);

app.get("/", (req, res) => {
  res.sendFile(path.join(__dirname, "/webapp/index.html"));
});

app.listen(port, () => {
  debug(`listening at port ${chalk.green(port)}`);

  // hot reload
  if (process.send) {
    process.send({
      event: "online",
      url: "http://localhost:6443/",
    });
  }
});
