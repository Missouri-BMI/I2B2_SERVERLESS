import React from "react";
import PropTypes from "prop-types";
import { ShepherdTour, TourMethods } from "react-shepherd";

import "./Tutorial.scss";
import { getSteps } from "./steps";

const tourOptions = {
  defaultStepOptions: {
    cancelIcon: {
      enabled: true,
    },
  },
  useModalOverlay: true,
};

let context = null;

export const cancelTutorial = () => {
  if (context && typeof context.cancel === "function") {
    context.cancel();
  }
};

export const Tutorial = React.memo((props) => {
  const { breakdown, onShowAgainChange, setup, networkName } = props;
  const stepProps = {
    onShowAgainChange,
    setup,
    networkName,
  };

  return (
    <ShepherdTour steps={getSteps(stepProps)} tourOptions={tourOptions}>
      <TourMethods>
        {(tourContext) => {
          if (!tourContext.isActive()) {
            tourContext.on("complete", breakdown);
            tourContext.on("cancel", breakdown);
            tourContext.start();
            context = tourContext;
          }
        }}
      </TourMethods>
    </ShepherdTour>
  );
});

Tutorial.propTypes = {
  setup: PropTypes.func.isRequired,
  breakdown: PropTypes.func.isRequired,
  onShowAgainChange: PropTypes.func.isRequired,
};
