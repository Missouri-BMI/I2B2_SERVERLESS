import { connect } from "react-redux";
import React from "react";
import PropTypes from "prop-types";
import { Route, Redirect } from "react-router-dom";
import {UnAuthorized} from "../components";
import {getCookie} from "../utilities";

export function WrappedAuthenticatedRoute(props) {
  const { component: Component, isAuthenticated, wasLoggedIn, ssoLogoutUrl, ...rest } = props;

  const params = new URLSearchParams(window.location.search);
  const authQueryParam = params.get('isAuth');

  return (
    <Route
      {...rest}
      render={() =>
        authQueryParam !== "false" ? (
          isAuthenticated ? (
          <Component {...props} />
        ) : (
            wasLoggedIn === true && getCookie("isSsoMode") === "true"  ?
              (<Redirect
                to={{ pathname: "/externalSSOLogout", state: { from: props.location } }}
              />): (<Redirect
          to={{ pathname: "/login", state: { from: props.location } }}
        />)
        )) : (<UnAuthorized/>)
      }
    />
  );
}

WrappedAuthenticatedRoute.propTypes = {
  component: PropTypes.shape({}).isRequired,
  isAuthenticated: PropTypes.bool.isRequired,
  location: PropTypes.shape({}).isRequired
};

const mapStateToProps = ({ session, networkConfig }) => ({
  isAuthenticated: session.user.isAuthenticated,
  wasLoggedIn: session.user.wasLoggedIn,
  ssoLogoutUrl:networkConfig.ssoLogoutUrl

});
export default connect(mapStateToProps)(WrappedAuthenticatedRoute);
