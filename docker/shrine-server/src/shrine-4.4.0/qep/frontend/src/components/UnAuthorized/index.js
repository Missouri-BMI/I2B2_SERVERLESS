import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { NetworkConfig } from "models";


import "./Unauth.scss";

export const WrappedUnauthorized = ({ networkConfig }) => {
  const unauthMessage = networkConfig.unauthorizedMessage;

  return (
    <p className="unauth">{ unauthMessage }</p>
  );
}

WrappedUnauthorized.propTypes = {
  networkConfig: PropTypes.shape(NetworkConfig.propTypes).isRequired,
};

const mapStateToProps = ({ networkConfig }) => ({
  networkConfig
});

const UnAuthorized = connect(mapStateToProps)(WrappedUnauthorized)


export { UnAuthorized };
