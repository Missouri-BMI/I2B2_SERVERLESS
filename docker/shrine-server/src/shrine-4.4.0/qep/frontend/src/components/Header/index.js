import { connect } from "react-redux";
import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { AppBar, Toolbar, Tabs, Tab, Avatar } from "@material-ui/core";

import { updateViewMode, fetchNetworkConfig } from "actions";
import { ViewMode, ViewModeTypes, NetworkConfig, User } from "models";
import { HeaderMenu } from "./HeaderMenu";
import "./Header.scss";

import { getBaseUrl } from "utilities";

const WrappedHeader = ({ dispatch, viewMode, user, networkConfig }) => {
  const handleTabChange = (event, newValue) => {
    if (newValue === ViewModeTypes.QUERY_RESULTS) {
      dispatch(updateViewMode(ViewModeTypes.QUERY_RESULTS));
    } else {
      dispatch(updateViewMode(ViewModeTypes.QUERY_DEFINITION));
    }
  };

  useEffect(() => {
    dispatch(fetchNetworkConfig());
  }, []);

  return (
    <AppBar position="static" className="Header">
      <Toolbar>
        <div className="banner-container">
          <img className="banner" alt="Your Logo Here" src={`${getBaseUrl()}staticData/webclient/logo`}/>
        </div>
        <div className="banner-text">
          {networkConfig.bannerText && <span className="banner-text-top">{networkConfig.bannerText}</span>}
        </div>
        {user.isAuthenticated && (
          <div className="tab-container">
            <Tabs
              onChange={handleTabChange}
              aria-label="disabled tabs example"
              value={viewMode.type}
            >
              <Tab
                label="Find Patients"
                value={ViewModeTypes.QUERY_DEFINITION}
              />
              <Tab label="View Results" value={ViewModeTypes.QUERY_RESULTS} />
              {networkConfig.nextStepsUrl && (
                <Tab
                  icon={
                    <a
                      href={networkConfig.nextStepsUrl}
                      target="_blank"
                      rel="noreferrer noopener"
                    >
                      Next Steps
                      <i
                        className="fa fa-external-link"
                        aria-hidden="true"
                      />{" "}
                    </a>
                  }
                  value={ViewModeTypes.NEXT_STEPS}
                />
              )}
            </Tabs>
            <HeaderMenu dispatch={dispatch} user={user} networkConfig={networkConfig} />
          </div>
        )}
      </Toolbar>
    </AppBar>
  );
};

WrappedHeader.propTypes = {
  viewMode: PropTypes.shape(ViewMode.propTypes).isRequired,
  dispatch: PropTypes.func.isRequired,
  user: PropTypes.shape(User.propTypes).isRequired,
  networkConfig: PropTypes.shape(NetworkConfig.propTypes).isRequired,
};

const mapStateToProps = ({ viewMode, session, networkConfig }) => ({
  viewMode,
  user: session.user,
  networkConfig,
});
const Header = connect(mapStateToProps)(WrappedHeader);
export { Header, WrappedHeader };
