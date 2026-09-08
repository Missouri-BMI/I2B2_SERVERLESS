import React from "react";
import PropTypes from "prop-types";

import { BarGraph } from "./BarGraph";

export const BarGraphList = React.memo(
  ({ graphDataList }) => {
    const dataLimitExceededList = [];
    const updateDataLimitedExceededList = (graphTitle) => {
      dataLimitExceededList.push('"' + graphTitle + '"');
    };

    return (
      <div>
        {graphDataList.map((graphData) => (
          graphData.results.length < 500 ? <BarGraph graphData={graphData} /> : updateDataLimitedExceededList(graphData.description)
        ))}

        {dataLimitExceededList.length > 0 &&
          <div>
            <span><i className="fa-solid fa-chart-bar dataLimit"></i></span>
            Unable to display {dataLimitExceededList.join(", ")}. See data in CSV.
          </div>
        }

      </div>
    );
  },
  (prevProps, nextProps) => {
    const prevPropsJson = JSON.stringify(prevProps);
    const nextPropsJson = JSON.stringify(nextProps);
    const shouldComponentUpdate = prevPropsJson === nextPropsJson;
    return shouldComponentUpdate;
  }
);

BarGraphList.defaultProps = {
  graphDataList: [],
};

BarGraphList.propTypes = {
  graphDataList: PropTypes.arrayOf(PropTypes.shape({})),
};
