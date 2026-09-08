import PropTypes from "prop-types";

export const ViewModeTypes = {
  NEXT_STEPS: "NEXT_STEPS",
  QUERY_RESULTS: "QUERY_RESULTS",
  QUERY_DEFINITION: "QUERY_DEFINITION",
  BREAKDOWNS: "BREAKDOWNS",
  HOME: "HOME",
  LOGIN: "LOGIN"
};

export const isValidViewMode = (viewMode) =>
  Object.values(ViewModeTypes).includes(viewMode);

export const ViewMode = ({
  type = ViewModeTypes.LOGIN,
  justLoggedIn = false
} = {}) => ({
  type,
  justLoggedIn
});

ViewMode.propTypes = {
  type: PropTypes.string,
  justLoggedIn: PropTypes.bool
};
