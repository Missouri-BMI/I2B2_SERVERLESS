
export const getCount = (value, noiseClamp, lowLimit, isAggregate) => {
  const PLUS_MINUS_CHAR = "\xB1";
  let valueWithNoise = `${value.toLocaleString()}` + ` patients`;

  if(noiseClamp !== undefined && noiseClamp > 0) {
    valueWithNoise =  `${value.toLocaleString()} ${PLUS_MINUS_CHAR} ` + noiseClamp + ` patients`;
  }

  let lowLimitText = lowLimit + " patients or fewer";

  if(isAggregate){
    lowLimitText = "Zero patients";
    valueWithNoise = "Up to " +  (value) + " patients";
  }

    return (value < 0 || (lowLimit !== undefined && lowLimit !== 0 && value <= lowLimit ))
      ?  lowLimitText : valueWithNoise;

};
