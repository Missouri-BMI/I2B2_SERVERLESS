import moment from "moment";

export const minAcceptableDate = moment("1900-01-01");
export const validateDates = (start, end) => {
  const startDate = moment(start);
  const endDate = moment(end);

  const validStartDate =
    startDate.isValid() && !startDate.isBefore(minAcceptableDate);
  const validEndDate =
    endDate.isValid() && !endDate.isBefore(minAcceptableDate);
  const validDateRange =
    validStartDate && validEndDate && startDate.isBefore(endDate);

  return {
    validStartDate,
    validEndDate,
    validDateRange,
  };
};
