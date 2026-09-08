import { connect } from "react-redux";
import React from "react";
import PropTypes from "prop-types";

import "./Footer.scss";

export const WrappedFooter = ({ user }) => {
  return <>{user.isAuthenticated ? <div className="py-3 Footer" >
    <div className="banner-text-bottom">
      Powered by the NCATS CTSA Program
    </div>
  </div> : null}</>;
};

WrappedFooter.propTypes = {
  user: PropTypes.shape({
    isAuthenticated: PropTypes.bool
  }).isRequired
};

const mapStateToProps = ({ session }) => ({
  user: session.user
});
const Footer = connect(mapStateToProps)(WrappedFooter);
export { Footer };
