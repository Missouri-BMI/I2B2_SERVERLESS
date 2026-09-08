import React, { useRef, useEffect, useState } from "react";
import PropTypes from "prop-types";
import { HorizontalBar } from "react-chartjs-2";

import { getCount } from "utilities";
import {
  convertDemographicsToGraphData,
  resultsToBarData,
  getBarGraphTickLabel,
} from "./barGraphHelpers";
import "./BarGraph.scss";

export const BarGraph = ({ graphData }) => {
  const graphTitle = graphData.description;

  const [barGraphData, setData] = useState(convertDemographicsToGraphData());
  const [noiseClamps, setNoiseClamps] = useState([]);
  const [lowLimits, setLowLimits] = useState([]);

  const [height, setHeight] = useState(Math.max(120, graphData.results.length * 22));

  const chartContainer = useRef(null);
  const [maxValue, setMaxValue] = useState(50);
  useEffect(() => {
    if (chartContainer.current) {
      const { labels, values, noiseClamps, lowLimits} = resultsToBarData(graphData.results);
      setNoiseClamps(noiseClamps);
      setLowLimits(lowLimits);

      const result = convertDemographicsToGraphData(labels, values);
      const [{ data }] = result.datasets;
      const newMaxValue = Math.max(...data);

      setData(result);
      if (newMaxValue > maxValue) {
        setMaxValue(newMaxValue);
      }
    }
  }, [graphData]);

  return (
    <div className="BarGraph">
      <HorizontalBar
        ref={chartContainer}
        height={height}
        aspectRatio={1.9}
        responsive={true}
        maintainAspectRatio={true}
        data={barGraphData}
        options={{
          legend: {
            display: false,
          },
          tooltips: {
            callbacks: {
              label: (tooltipItem, data) => {
                const tooltiplabel = getCount(tooltipItem.value, noiseClamps[tooltipItem.index], lowLimits[tooltipItem.index], true);
                return tooltiplabel;
              },
            },
          },
          title: {
            display: true,
            text: graphTitle,
            fontStyle: "normal",
            fontSize: 18,
          },
          labels: {
            text: "test",
          },
          layout: {
            padding: {
              left: 170
            }
          },
          scales: {
            yAxes: [
              {
                ticks: {
                  callback: (value, index, values) => {
                    return getBarGraphTickLabel(value);
                  },
                  mirror: true,
                  padding: 170
                },
              },
            ],
            xAxes: [
              {
                ticks: {
                  min: 0,
                  max: maxValue,
                  callback: function(val, index, values) {
                    // Hide the last label
                    return index !== (values.length-1) ? val : '';
                  }
                },
              },
            ],
          },
        }}
      />
    </div>
  );
};

BarGraph.propTypes = {
  graphData: PropTypes.shape({
    description: PropTypes.string,
    results: PropTypes.arrayOf({}),
  }).isRequired,
};
