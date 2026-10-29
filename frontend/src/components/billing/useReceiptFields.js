import { useState } from 'react';
import { receiptFileError } from '@/lib/billing';
import { formatAmountInput, parseAmountInput } from '@/lib/bankTransferReceipt';

export function useReceiptFields({ initialAmount, defaultHolderName = '', onEdit }) {
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [operationNumber, setOperationNumber] = useState('');
  const [originAccount, setOriginAccount] = useState('');
  const [holderName, setHolderName] = useState(defaultHolderName);
  const [bankName, setBankName] = useState('');
  const [showBank, setShowBank] = useState(false);
  const [amountText, setAmountText] = useState(() => formatAmountInput(initialAmount));
  const [amountTouched, setAmountTouched] = useState(false);

  function edited(setter) {
    return (value) => {
      onEdit?.();
      setter(value);
    };
  }

  function selectFile(selected) {
    onEdit?.();
    setFile(selected);
    setFileError(receiptFileError(selected));
  }

  function removeFile() {
    onEdit?.();
    setFile(null);
    setFileError(null);
  }

  function editAmount(value) {
    onEdit?.();
    setAmountTouched(true);
    setAmountText(value);
  }

  function suggestAmount(total) {
    if (!amountTouched) setAmountText(formatAmountInput(total));
  }

  const amount = parseAmountInput(amountText);

  function normalizeAmount() {
    if (Number.isFinite(amount) && amount > 0) setAmountText(formatAmountInput(amount));
  }

  return {
    file,
    fileError,
    operationNumber,
    originAccount,
    holderName,
    bankName,
    showBank,
    amountText,
    amount,
    selectFile,
    removeFile,
    editAmount,
    normalizeAmount,
    suggestAmount,
    revealBank: () => setShowBank(true),
    setOperationNumber: edited(setOperationNumber),
    setOriginAccount: edited(setOriginAccount),
    setHolderName: edited(setHolderName),
    setBankName: edited(setBankName),
  };
}
