export const breakdowns = [
  {
    resultType: {
      name: "PATIENT_AGE_COUNT_XML",
      isBreakdown: true,
      i2b2Options: {
        description: "Age patient breakdown",
        displayType: "CATNUM",
      },
      id: null,
    },
    results: [
      {
        dataKey: "  0-9 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  10-17 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  18-34 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  35-44 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  45-54 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  55-64 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  65-74 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "  75-84 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: ">= 65 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: ">= 85 years old",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "Not recorded",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
    ],
  },
  {
    resultType: {
      name: "PATIENT_GENDER_COUNT_XML",
      isBreakdown: true,
      i2b2Options: {
        description: "Gender patient breakdown",
        displayType: "CATNUM",
      },
      id: null,
    },
    results: [
      {
        dataKey: "Female",
        value: 510,
        changeDate: 1604516535465,
      },
      {
        dataKey: "Male",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
      {
        dataKey: "Unknown",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
      },
    ],
  },
  {
    resultType: {
      name: "PATIENT_RACE_COUNT_XML",
      isBreakdown: true,
      i2b2Options: {
        description: "Race patient breakdown",
        displayType: "CATNUM",
      },
      id: null,
    },
    results: [
      {
        dataKey: "Aleutian",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9
      },
      {
        dataKey: "American Indian",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Asian",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Asian Pacific Islander",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Black",
        value: 230,
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Eskimo",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Hispanic",
        value: 110,
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Indian",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Middle Eastern",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Multiracial",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Native American",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Navajo",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Not recorded",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Oriental",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Other",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "White",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
    ],
  },
  {
    resultType: {
      name: "PATIENT_VITALSTATUS_COUNT_XML",
      isBreakdown: true,
      i2b2Options: {
        description: "Vital Status patient breakdown",
        displayType: "CATNUM",
      },
      id: null,
    },
    results: [
      {
        dataKey: "Deceased",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Deferred",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Living",
        value: 500,
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
      {
        dataKey: "Not recorded",
        value: Math.ceil(Math.random() * 1000),
        changeDate: 1604516535465,
        noiseClamp: 9,
        lowLimit: 3,
      },
    ],
  },
];
