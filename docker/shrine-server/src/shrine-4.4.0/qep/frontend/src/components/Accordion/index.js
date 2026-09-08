import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import {
  ExpansionPanel,
  ExpansionPanelSummary,
  ExpansionPanelDetails,
  Icon,
  Button
} from "@material-ui/core";

import "./Accordion.scss";

function Accordion({ heading, children, renderAsExpanded, onExpandedChange, forceExpand, forceClose, ...otherProps }) {
  const [isExpanded, setIsExpanded] = useState(
    Accordion.defaultProps.renderAsExpanded
  );

  const handleClick = () => {
    setIsExpanded((oldIsExpanded) => !oldIsExpanded);
  };

  useEffect(() => {
    if (forceExpand) {
      setIsExpanded(true);
    }
    if (forceClose) {
      setIsExpanded(false);
    }
  }, [forceExpand, forceClose])

  useEffect(() => {
    if (renderAsExpanded !== isExpanded) {
      setIsExpanded(renderAsExpanded);
    }
  }, [renderAsExpanded]);

  useEffect(() => {
    if (onExpandedChange) {
      onExpandedChange(isExpanded);
    }
  }, [isExpanded]);

  return (
    <ExpansionPanel className="Accordion  tutorialStep3" expanded={isExpanded} {...otherProps}>
      <ExpansionPanelSummary className="accordion-toggle">
        <Button className="heading" onClick={handleClick}>
          {heading}
          <Icon className="fa fa-chevron-down accordion-chevron" />
        </Button>
      </ExpansionPanelSummary>
      <ExpansionPanelDetails className="accordion-content">
        {children}
      </ExpansionPanelDetails>
    </ExpansionPanel>
  );
}

Accordion.defaultProps = {
  renderAsExpanded: false,
  onExpandedChange: null,
};

Accordion.propTypes = {
  heading: PropTypes.string.isRequired,
  children: PropTypes.oneOfType([
    PropTypes.element,
    PropTypes.arrayOf(PropTypes.element),
  ]).isRequired,
  renderAsExpanded: PropTypes.bool,
  onExpandedChange: PropTypes.func,
  forceExpand: PropTypes.bool,
  forceClose: PropTypes.bool,
};

export default React.memo(Accordion);
