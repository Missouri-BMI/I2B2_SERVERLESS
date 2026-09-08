import React, { useEffect, useState, useRef } from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import moment from "moment";
import {keepAlive, fetchNetworkConfig, clearLogin} from "actions";
import { Session, NetworkConfig } from "models";
import { IdleTimerDialog } from "./IdleTimerDialog";
import { cancelTutorial } from "../QueryDefinitionView/Tutorial";

export const WrappedIdleTimer = ({ dispatch, session, onTimeoutWarning, networkConfig }) => {

  const [showInactivityAlert, setShowInactivityAlert] = useState(false);
  const [autoLogoutTimer, setAutoLogoutTimer] = useState(null);
  const [lastActiveTime, setLastActiveTime] = useState(Date.now());
  const lastActiveTimeRef = useRef(lastActiveTime);
  lastActiveTimeRef.current = lastActiveTime;
  const showInactivityAlertRef = useRef(showInactivityAlert);
  showInactivityAlertRef.current = showInactivityAlert;

  useEffect(() => {
    dispatch(fetchNetworkConfig());
  }, []);

  const { user } = session;

  const clearLogoutTimer = () => {
    clearTimeout(autoLogoutTimer);
    setAutoLogoutTimer(null);
  };

  const handleCloseIdleTimerDialog = () => {
    setShowInactivityAlert(false);
    clearLogoutTimer();
  };

  const handleExtendSession = () => {
    setShowInactivityAlert(false);
    clearLogoutTimer();
    dispatch(keepAlive());
  };

  const handleLogout = () => {
    cancelTutorial();

    if (onTimeoutWarning) {
      onTimeoutWarning();
    }
    handleCloseIdleTimerDialog();

    dispatch(clearLogin());
  };

  const handleOpenIdleTimerDialog = () => {

    setShowInactivityAlert(true);

    const logoutTimer = setTimeout(() => {
      handleLogout();
      }, session.sessionTimeoutMs * 0.2);

    setAutoLogoutTimer(logoutTimer);
  };

  useEffect(() => {
    if (user.isAuthenticated) {
      dispatch(keepAlive());
    }
  }, []);

  useEffect(() => {
    const timeoutMs = session.sessionTimeoutMs * 0.8;
    if (session.active) {
      const interval = setTimeout(() => {
        let currentTime = Date.now();
        let duration = moment.duration(
          moment(Number(currentTime)).diff(Number(lastActiveTimeRef.current))
        );

        let inactivityTime = duration.asMilliseconds();

        if (inactivityTime >= session.sessionTimeoutMs * 0.7) {
          handleOpenIdleTimerDialog();
        } else {
          dispatch(keepAlive());
        }
      }, timeoutMs);

      return () => {
        clearTimeout(interval);
      };
    }
  }, [session.active]);

  const resetInactivityTimer = () => {
    let currentTime = Date.now();

    let duration = moment.duration(
      moment(Number(currentTime)).diff(Number(lastActiveTime))
    );

    let inactivityTime = duration.asMilliseconds();

    if (!showInactivityAlertRef.current && inactivityTime >= session.sessionTimeoutMs) {
        handleLogout();
    }else{
      setLastActiveTime(currentTime);
    }
  };

  useEffect(() => {
    window.addEventListener("mousemove", resetInactivityTimer);
    window.addEventListener("onclick", resetInactivityTimer);
    window.addEventListener("keypress", resetInactivityTimer);

    return () => {
      window.removeEventListener("mousemove", resetInactivityTimer);
      window.removeEventListener("onclick", resetInactivityTimer);
      window.removeEventListener("keypress", resetInactivityTimer);
    };
  }, [lastActiveTime]);

  return (
    <div className="IdleTimer">
      <IdleTimerDialog
        open={showInactivityAlert}
        extendSession={handleExtendSession}
        logOut={handleLogout}
      />
    </div>
  );
};

WrappedIdleTimer.defaultProps = {
  onTimeoutWarning: null,
};

WrappedIdleTimer.propTypes = {
  dispatch: PropTypes.func.isRequired,
  session: PropTypes.shape(Session.propTypes).isRequired,
  onTimeoutWarning: PropTypes.func,
  networkConfig: PropTypes.shape(NetworkConfig.propTypes).isRequired,
};

const mapStateToProps = ({ session, networkConfig }) => ({
  session,
  networkConfig,
});
const IdleTimer = connect(mapStateToProps)(WrappedIdleTimer);

export { IdleTimer };
