import { QueryTerm } from "models";
import { renderCheckbox } from "./renderCheckbox";

const delayedCheckboxRender = (props) => {
  setTimeout(() => {
    renderCheckbox(props);
  }, 500);
};

export const getSteps = (props) => [
  {
    id: "intro",
    title: `Welcome to the ${props.networkName} network!`,
    text: [
      `We’ll show you how to construct your inclusion and exclusion criteria to obtain patient counts from across the ${props.networkName} Network.`,
    ],
    buttons: [
      {
        type: "next",
        text: "Next",
      },
    ],
    when: {
      show() {
        delayedCheckboxRender(props);
      },
    },
  },
  {
    id: "searchConcepts",
    title: "Search for Concepts",
    text: [
      `Search or browse medical terminologies for concepts that describe your clinical trial's inclusion and exclusion criterion.`,
    ],
    attachTo: { element: ".tutorialStep1", on: "right" },
    buttons: [
      {
        type: "next",
        text: "Next",
      },
    ],
  },

  {
    id: "findPatients",
    title: "Define Patient Population",
    text: [
      `Drag and drop concepts into the first box. Panels will default to an inclusion panel. Toggle to specify exclusion, or to create an event based panel. Additional panels will automatically appear.`,
    ],
    attachTo: { element: ".tutorialStep2", on: "top" },
    buttons: [
      {
        type: "next",
        text: "Next",
      },
    ],
  },
  {
    id: "configureGroupSettings",
    title: "Specify Date Range or Occurrences",
    text: [
      "Add optional date range or require multiple occurrences of concepts.",
    ],
    options: {
      classes: ["configureGroupSettings"],
    },
    attachTo: { element: ".tutorialStep3", on: "top" },
    beforeShowPromise: () =>
      new Promise((resolve) => {
        props.setup(
          QueryTerm({
            path: "mock-path",
            displayName: "Medical Concept For Tutorial",
            conceptCategory: "Diagnosis",
          })
        );
        setTimeout(() => {
          resolve();
        }, 320);
      }),
    buttons: [
      {
        type: "next",
        text: "Next",
      },
    ],
  },
  {
    id: "startQuery",
    title: `Search ${props.networkName} network`,
    text: [
      'Specify a name, choose to include demographic distribution data, and click "Count Patients" to view results.',
    ],
    when: {
      show() {
        delayedCheckboxRender(props);
      },
    },
    attachTo: { element: ".tutorialStep4", on: "top" },
    buttons: [
      {
        type: "next",
        text: "Finish",
      },
    ],
  },
];
