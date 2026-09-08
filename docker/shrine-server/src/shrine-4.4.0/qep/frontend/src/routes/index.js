import { connect } from "react-redux";
import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { Route, Switch } from "react-router-dom";

import { ViewModeTypes, ViewMode } from "models";
import { Login, Researcher } from "pages";
import AuthenticatedRoute from "./AuthenticatedRoute";

const titleMap = {
  [ViewModeTypes.LOGIN]: "Network - Log In",
  [ViewModeTypes.QUERY_DEFINITION]: "Network - Find Patients",
  [ViewModeTypes.QUERY_RESULTS]: "Network - View Results"
};

export const WrappedRoutes = ({ networkConfig, viewMode, dispatch }) => {
  useEffect(() => {
    if (document && networkConfig.name !== null) {
      document.title = `${networkConfig.name}  ${titleMap[viewMode.type]}`;
    }
  }, [viewMode.type, networkConfig.name]);
  return (
    <Switch>
      <Route path="/login" component={Login} />
      <AuthenticatedRoute path="/" exact component={Researcher} />
      <Route
        path="/externalSSOLogout"
        component={() => {
          window.location.replace(networkConfig.ssoLogoutUrl);
          return null;
        }}
      />
    </Switch>
  );
};

WrappedRoutes.propTypes = {
  viewMode: PropTypes.shape(ViewMode.propTypes).isRequired,
  networkName: PropTypes.string.isRequired
};

const mapStateToProps = ({ viewMode, networkConfig }) => ({ viewMode, networkConfig});
const Routes = connect(mapStateToProps)(WrappedRoutes);

export { Routes };
