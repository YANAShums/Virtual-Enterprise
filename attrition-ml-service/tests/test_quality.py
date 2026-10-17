import joblib
import numpy as np
import pandas as pd
import pytest
from app.features import ALL_FEATURES
from app.ml_core import TrainingError, split_by_employee, predict_with_factors
from app.quality import fit_calibrated,temporal_split,temporal_backtest,quality_report
from tests.test_core import make_dataset


def dataset():
    df=make_dataset()
    df['snapshot_date']=pd.Timestamp('2024-01-01')
    return df


def test_fit_calibration_and_test_employees_never_overlap_and_artifact_reloads(tmp_path):
    df=dataset();train,test=split_by_employee(df)
    pipe,meta=fit_calibrated(df.iloc[train].reset_index(drop=True))
    a=set(meta['fit_employee_ids']);b=set(meta['calibration_employee_ids']);c=set(df.employee_id.iloc[test])
    assert not a&b and not a&c and not b&c
    path=tmp_path/'calibrated.joblib';joblib.dump(pipe,path);loaded=joblib.load(path)
    np.testing.assert_allclose(pipe.predict_proba(df[ALL_FEATURES]),loaded.predict_proba(df[ALL_FEATURES]))
    assert len(predict_with_factors(loaded,df.head()))==5
    report=quality_report(df,pipe,test,meta)
    assert report['business_validation']=='NOT_VALIDATED'
    assert report['temporal_backtest']['status']=='INSUFFICIENT_DATA'
    assert 0<=report['hr_capacity']['precision']<=1
    assert report['data_origin_counts']=={'UNKNOWN':len(df)}


def test_temporal_evaluation_purges_employees_and_unmatured_labels():
    df=dataset()
    employee_numbers=df.employee_id.astype(str).str.extract(r'(\d+)')[0].astype(int)
    df['snapshot_date']=pd.to_datetime(np.where(employee_numbers<300,'2024-01-01','2025-01-01'))
    train,test=temporal_split(df)
    assert not set(df.employee_id.iloc[train]) & set(df.employee_id.iloc[test])
    assert df.snapshot_date.iloc[train].max()+pd.Timedelta(days=90)<df.snapshot_date.iloc[test].min()
    report=temporal_backtest(df)
    assert report['status']=='AVAILABLE',report
    assert report['metrics']['n']==len(test)


def test_short_history_refuses_temporal_claim():
    df=dataset();df['snapshot_date']=pd.date_range('2024-01-01',periods=len(df),freq='h')
    assert temporal_backtest(df)['status']=='INSUFFICIENT_DATA'
    with pytest.raises(TrainingError):fit_calibrated(df.head(2))
