import { connect } from "react-redux";
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import Button from "@material-ui/core/Button";
import TextField from "@material-ui/core/TextField";
import { Select, MenuItem } from '@material-ui/core';
import Typography from "@material-ui/core/Typography";
import Container from "@material-ui/core/Container";

import "./login.scss";

import { User } from "models";
import { loginUser, clearLogin, clearWarning } from "actions";

import { getCookie } from "utilities";

export const WrappedLogin = ({ toBase64, dispatch, history, user, networkConfig }) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState(null);
  const canSubmit = username.length > 0 && password.length > 0;
  const hasError = user.error.hasError;
  const errorText = hasError ? user.error.message : "";
  const [redirectUrl, setRedirectUrl] = useState("");

  const isSsoMode = getCookie("isSsoMode") === "true";

  const handleUsernameChange = (event) => {
    if (hasError) dispatch(clearWarning());
    setUsername(event.target.value);
  }
  const handlePasswordChange = (event) => {
    if (hasError) dispatch(clearWarning());
    setPassword(event.target.value);
  }
  const handleSubmit = (event) => {
    event.preventDefault();
    if (canSubmit) {
      dispatch(
        loginUser({
          username,
          token
        })
      );
    }
  };

  useEffect(() => {
    if (isSsoMode) {
      dispatch(
        loginUser({})
      );

      let baseUrl = window.location.protocol + "//" + window.location.hostname +
        (window.location.port ? ':' + window.location.port: '');
      let fullpath = baseUrl+"/shrine-api/shrine-webclient/";
      setRedirectUrl(fullpath);
    }
    if (user.isAuthenticated) {
      dispatch(clearLogin());
    }
  }, []);

  useEffect(() => {
    setToken(`${toBase64(`${username}:${password}`)}`);
  }, [username, password]);

  useEffect(() => {
    if (user.isAuthenticated) {
      history.push("/");
    }
  }, [user.isAuthenticated]);

  const getMenuItemsFromMap = (map) => {
    return map
      ? Object.entries(map).map(([key, value]) => {
        return (
          <MenuItem value={value}>{key}</MenuItem>
        )})
      : <MenuItem value='none'>IdP Not Configured</MenuItem>;
  };

  return (
    isSsoMode &&
    <div className="Login">
      <Container component="main" maxWidth="xs">
        <div className="login-container">
          <Typography component="h1" variant="h5">
            Sign in
          </Typography>
          <div>
            <h2>Please select an Identity Provider:</h2>
            <form className="login-form" action="/Shibboleth.sso/Login" method="GET">
              <label htmlFor="idpSelect">Choose an Identity Provider:</label>
              <Select id="idpSelect" name="entityID" fullWidth>
                {getMenuItemsFromMap(networkConfig.ssoLinks)}
              </Select>
              <br/>
              <input type="hidden" name="RelayState" value=""/>
              <input type="hidden" name="target" id="redirectUrl" value={redirectUrl}/>
              <Button
                fullWidth
                variant="contained"
                color="primary"
                type="submit">
                Sign In
              </Button>
            </form>
          </div> </div>
      </Container>
    </div>

    || !isSsoMode &&
    <div className="Login">
      <Container component="main" maxWidth="xs">
        <div className="login-container">
          <Typography component="h1" variant="h5">
            Sign in
          </Typography>
          <form className="login-form" onSubmit={handleSubmit}>
            <TextField
              error={hasError}
              variant="outlined"
              margin="normal"
              fullWidth
              label="Username"
              name="username"
              value={username}
              autoComplete="username"
              autoFocus
              onChange={handleUsernameChange}
            />
            <TextField
              error={hasError}
              helperText={errorText}
              variant="outlined"
              margin="normal"
              fullWidth
              name="password"
              label="Password"
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={handlePasswordChange}
            />
            <Button
              fullWidth
              variant="contained"
              color="primary"
              type="submit"
              disabled={!canSubmit}>
              Sign In
            </Button>
          </form>
        </div>
      </Container>
    </div>
  );
};

WrappedLogin.propTypes = {
  dispatch: PropTypes.func.isRequired,
  toBase64: PropTypes.func,
  user: PropTypes.shape(User.propTypes).isRequired,
  history: PropTypes.shape({
    push: PropTypes.func
  }).isRequired
};

WrappedLogin.defaultProps = {
  toBase64: (window && window.btoa) || ((arg) => arg)
};

const mapStateToProps = ({ session, networkConfig }) => ({
  user: session.user,
  networkConfig
});
const Login = connect(mapStateToProps)(WrappedLogin);
export { Login };
