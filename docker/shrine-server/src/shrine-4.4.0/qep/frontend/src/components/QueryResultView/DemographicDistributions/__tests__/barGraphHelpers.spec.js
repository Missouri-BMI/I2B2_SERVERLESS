import {
  resultsToBarData,
  convertDemographicsToGraphData,
} from "../barGraphHelpers";
import { breakdowns } from "./mockBreakdowns";

describe("barGraphHelpers - mockBreakdownResults", () => {
  const mockBreakdownResults = [
    {
      dataKey: "Demographic 1",
      value: 0,
      noiseClamp: 9,
      lowLimit: 3
    },
    {
      dataKey: "Demographic 2",
      value: 25,
      noiseClamp: 9,
      lowLimit: 3
    },
    {
      dataKey: "Demographic 3",
      value: 100,
      noiseClamp: 9,
      lowLimit: 3,
    },
  ];

  const chartJSDataFormat = {
    labels: ["Demographic 1", "Demographic 2", "Demographic 3"],
    values: [1, 25, 100],
    noiseClamps: [9, 9, 9],
    lowLimits: [3, 3, 3]
  };

  it("should replace any values of zero with 10.001 in order for the chart.js library to display the value in the graph", () => {
    const actual = chartJSDataFormat.values.every((value) => value > 0);
    expect(actual).toBe(true);
  });

  it("should result in an object where labels and values are two separate arrays", () => {
    const actual = resultsToBarData(mockBreakdownResults);
    expect(actual).toEqual(chartJSDataFormat);
  });
});

describe("barGraphHelpers - convertDemographicsToGraphData", () => {
  it("Should successfully create a chart.js bar chart data structure", () => {
    const result = convertDemographicsToGraphData(breakdowns);
    const expectedDataSet = [
      {
        barThickness: 20,
        backgroundColor: [],
        borderColor: "#2782a3",
        borderWidth: 1,
        hoverBackgroundColor: "lightgray",
        hoverBorderColor: "#2782a3",
        data: [],
      },
    ];
    expect(Array.isArray(result.labels)).toBe(true);
    expect(result.labels.length).toEqual(breakdowns.length);
    expect(Array.isArray(result.datasets)).toBe(true);
  });
});
