import randomColor from "randomcolor";
import range from "lodash.range";

const numberOfColors = 100;
const barDataShape = { labels: [], values: [], noiseClamps: [] , lowLimits: []};
const colorScheme = range(0, numberOfColors).map(() => randomColor());

export const getBarGraphTickLabel = (label, maxLength = 24) => {
  const trimmedLabel = label.trim();
  return trimmedLabel.length > maxLength
    ? `${trimmedLabel.substring(0, maxLength)}...`
    : trimmedLabel;
};

export const resultsToBarData = (breakdownResults) => {
  return breakdownResults.reduce((barData, currentResult) => {
    const { value } = currentResult;
    const labels = [...barData.labels, currentResult.dataKey.trim()];
    const values = [
      ...barData.values,
      value === 0 ? 1 : value,
    ];

    const noiseClamps = [...barData.noiseClamps, currentResult.noiseClamp];
    const lowLimits = [...barData.lowLimits, currentResult.lowLimit];

    return { labels, values, noiseClamps, lowLimits };
  }, barDataShape);
};

export const convertDemographicsToGraphData = (
  graphLabels = [],
  graphValues = []
) => {
  const newColors = colorScheme.slice(0, graphLabels.length);

  return {
    labels: graphLabels,
    datasets: [
      {
        barThickness: "30",
        backgroundColor: newColors,
        borderColor: "#2782a3",
        borderWidth: 1,
        hoverBackgroundColor: "lightgray",
        hoverBorderColor: "#2782a3",
        data: graphValues,
      },
    ],
  };
};
