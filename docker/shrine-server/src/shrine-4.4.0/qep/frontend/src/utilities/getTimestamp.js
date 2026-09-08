import moment from 'moment';

export const getTimestamp = (earlierUTC, laterUTC = Date.now()) => {
  const diffInSeconds = moment(Number(laterUTC)).diff(Number(earlierUTC), 'seconds');
  const diffInDays = moment(Number(laterUTC)).diff(Number(earlierUTC), 'days', true);

  if (diffInSeconds < 60) {
    const seconds = Math.max(0, diffInSeconds);
    return `${seconds} seconds ago`;
  }
  if (diffInDays < 1) {
    return moment(earlierUTC).fromNow();
  }
  return moment(Number(earlierUTC)).format('MM/DD/YY HH:mm:ss');
};
