import PropTypes from "prop-types";

export const Fav = ({
  faved = false,
} = {}) => ({
  faved,
});

Fav.propTypes = {
  faved: PropTypes.bool.isRequired,
  changeDate: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};
