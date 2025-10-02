import { Autocomplete, Chip, TextField, CircularProgress } from '@mui/material';
import type { SyntheticEvent } from 'react';

interface SymbolSelectorProps {
  options: string[];
  value: string[];
  loading?: boolean;
  error?: boolean;
  helperText?: string;
  label?: string;
  onChange: (symbols: string[]) => void;
}

const normalize = (symbols: string[]) =>
  symbols
    .map((symbol) => symbol.trim().toUpperCase())
    .filter((symbol, index, array) => symbol && array.indexOf(symbol) === index);

export function SymbolSelector({
  options,
  value,
  loading = false,
  error = false,
  helperText,
  label = 'Select symbols',
  onChange
}: SymbolSelectorProps) {
  const handleChange = (_event: SyntheticEvent, newValue: string[]) => {
    onChange(normalize(newValue));
  };

  return (
    <Autocomplete
      multiple
      options={options}
      loading={loading}
      value={value}
      onChange={handleChange}
      filterSelectedOptions
      renderTags={(tagValue, getTagProps) =>
        tagValue.map((option, index) => {
          const { key, ...tagProps } = getTagProps({ index });
          return <Chip key={key} {...tagProps} variant="outlined" label={option} />;
        })
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          error={error}
          helperText={helperText}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress color="inherit" size={20} /> : null}
                {params.InputProps.endAdornment}
              </>
            )
          }}
        />
      )}
    />
  );
}

export default SymbolSelector;
