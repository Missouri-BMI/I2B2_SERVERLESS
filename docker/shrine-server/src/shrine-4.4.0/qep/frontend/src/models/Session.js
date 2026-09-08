import PropTypes from "prop-types";

import { User } from "./User";

export const Session = ({
  active = false,
  timeout = null,
  user = User(),
  sessionTimeoutMs = null,
} = {}) => ({
  active,
  timeout,
  user,
  sessionTimeoutMs,
});

Session.propTypes = {
  active: PropTypes.bool.isRequired,
  timeout: PropTypes.number,
  sessionTimeoutMs: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  user: PropTypes.shape(User.propTypes),
};
