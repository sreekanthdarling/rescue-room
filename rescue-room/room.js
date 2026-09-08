export const room = {
  duration: 90000,
  code: '3846',
  clues: [
    {id:'note',label:'Inspect field notes',tag:'01 / FIELD NOTES',title:'The last instruction',body:'“Follow the night: MOON → STAR → EYE → SUN. The clock cannot help you.”',hint:'This is the order of the four digits.'},
    {id:'chart',label:'Inspect symbol chart',tag:'02 / WALL CHART',title:'An unfamiliar alphabet',body:'☀ SUN = 5\n◉ EYE = 3\n☾ MOON = 2\n✦ STAR = 7',hint:'These are reference values, not the final digits.'},
    {id:'drawer',label:'Inspect open drawer',tag:'03 / MAINTENANCE CARD',title:'One step ahead',body:'“Door calibration: each symbol’s reference value must be increased by ONE. Then enter the digits in the order from the field notes.”',hint:'Combine all three clues at the exit keypad.'}
  ]
};
