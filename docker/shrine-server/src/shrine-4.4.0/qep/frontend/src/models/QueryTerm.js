import PropTypes from "prop-types";
import { TermConstraint } from "./TermConstraint";

export const QueryTerm = ({
  path = null,
  displayName = null,
  highlightedName = null,
  conceptCategory = null,
  children = new Map(),
  constraint = TermConstraint(),
  isLab = null,
  ...rest
}) => ({
  path,
  displayName,
  highlightedName,
  conceptCategory,
  children,
  constraint,
  isLab,
  ...rest,
});

QueryTerm.propTypes = {
  path: PropTypes.string.isRequired,
  displayName: PropTypes.string.isRequired,
  highlightedName: PropTypes.string,
  conceptCategory: PropTypes.string.isRequired,
  children: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  constraint: PropTypes.shape(TermConstraint.propTypes).isRequired,
  isLab: PropTypes.bool,
};
