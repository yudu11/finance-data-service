import { Stack, TextField } from '@mui/material';
import type { ChangeEvent } from 'react';

interface DateRangeSelectorProps {
  startDate?: string;
  endDate?: string;
  onChange: (range: { startDate?: string; endDate?: string }) => void;
}

function DateRangeSelector({ startDate, endDate, onChange }: DateRangeSelectorProps) {
  const handleStartChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange({ startDate: event.target.value || undefined, endDate });
  };

  const handleEndChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange({ startDate, endDate: event.target.value || undefined });
  };

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField
        label="Start date"
        type="date"
        value={startDate ?? ''}
        onChange={handleStartChange}
        InputLabelProps={{ shrink: true }}
      />
      <TextField
        label="End date"
        type="date"
        value={endDate ?? ''}
        onChange={handleEndChange}
        InputLabelProps={{ shrink: true }}
      />
    </Stack>
  );
}

export default DateRangeSelector;
