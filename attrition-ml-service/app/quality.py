"""Leakage-safe calibration and temporal evaluation; no claim of business validation."""
from __future__ import annotations
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from .features import ALL_FEATURES
from .ml_core import TrainingError, build_pipeline, split_by_employee, compute_metrics


class CalibratedPipeline:
    def __init__(self, base, calibration):
        self.base=base
        self.calibration=calibration

    @property
    def named_steps(self):
        return self.base.named_steps

    @property
    def contribution_scale(self):
        return float(self.calibration.coef_[0,0])

    def predict_proba(self, X):
        scores=self.base.decision_function(X).reshape(-1,1)
        return self.calibration.predict_proba(scores)


def fit_calibrated(df, seed=42):
    """Calibration employees never appear in fitting; caller separately owns the test set."""
    fit,cal=split_by_employee(df,0.25,seed)
    if min(df.iloc[fit].employee_id.nunique(),df.iloc[cal].employee_id.nunique())<5:
        raise TrainingError('Для независимой калибровки нужно минимум 5 сотрудников в каждой группе')
    base=build_pipeline(seed).fit(df.iloc[fit][ALL_FEATURES],df.y.iloc[fit])
    calibration=LogisticRegression(random_state=seed,max_iter=1000).fit(
        base.decision_function(df.iloc[cal][ALL_FEATURES]).reshape(-1,1),df.y.iloc[cal])
    if float(calibration.coef_[0,0])<=0:
        raise TrainingError('Калибровочная группа не подтверждает направление сигнала модели')
    return CalibratedPipeline(base,calibration), {
        'method':'sigmoid_on_independent_employees',
        'fit_employee_ids':sorted(set(df.employee_id.iloc[fit])),
        'calibration_employee_ids':sorted(set(df.employee_id.iloc[cal]))}


def temporal_split(df,horizon_days=90):
    dates=pd.to_datetime(df.snapshot_date)
    unique=sorted(dates.unique())
    if len(unique)<2:
        raise TrainingError('Для временной оценки нужна история с несколькими датами')
    cutoff=pd.Timestamp(unique[max(1,int(len(unique)*0.8)) if len(unique)>2 else 1])
    test=np.flatnonzero((dates>=cutoff).to_numpy())
    test_employees=set(df.employee_id.iloc[test])
    # All training outcomes must already be known before the first test date.
    train=np.flatnonzero(((dates+pd.Timedelta(days=horizon_days)<cutoff)
        & ~df.employee_id.isin(test_employees)).to_numpy())
    if len(train)<40 or len(test)<20 or df.y.iloc[train].nunique()<2 or df.y.iloc[test].nunique()<2:
        raise TrainingError('Недостаточно независимой поздней истории после исключения сотрудников и окна метки')
    return train,test


def temporal_backtest(df,seed=42,horizon_days=90):
    try:
        train,test=temporal_split(df,horizon_days)
        pipe,_=fit_calibrated(df.iloc[train].reset_index(drop=True),seed)
        probability=pipe.predict_proba(df.iloc[test][ALL_FEATURES])[:,1]
        metrics=compute_metrics(df.y.iloc[test].to_numpy(),probability)
        segments={}
        for department in sorted(df.department_code.iloc[test].unique()):
            mask=(df.department_code.iloc[test].to_numpy()==department)
            y=df.y.iloc[test].to_numpy()[mask]
            segments[str(department)]={'status':'AVAILABLE','metrics':compute_metrics(y,probability[mask])} if len(y)>=30 and len(set(y))==2 else {'status':'INSUFFICIENT_DATA','rows':len(y)}
        return {'status':'AVAILABLE','cutoff':str(df.snapshot_date.iloc[test].min()),
                'train_rows':len(train),'test_rows':len(test),'metrics':metrics,'departments':segments}
    except TrainingError as ex:
        return {'status':'INSUFFICIENT_DATA','reason':str(ex)}


def quality_report(df,pipe,test,calibration,seed=42,horizon_days=90):
    y=df.y.iloc[test].to_numpy();X=df.iloc[test][ALL_FEATURES]
    proba=pipe.predict_proba(X)[:,1]
    top=max(1,int(np.ceil(len(y)*0.1)));idx=np.argsort(-proba,kind='stable')[:top]
    bins=[]
    for low in np.arange(0,1,0.2):
        mask=(proba>=low)&(proba<(low+0.2) if low<0.8 else proba<=1)
        if mask.any():bins.append({'min':round(float(low),1),'n':int(mask.sum()),
            'mean_probability':float(proba[mask].mean()),'observed_rate':float(y[mask].mean())})
    temporal=temporal_backtest(df,seed,horizon_days)
    origins=df.get('data_origin',pd.Series(['UNKNOWN']*len(df))).value_counts().to_dict()
    return {'business_validation':'NOT_VALIDATED',
        'reason':'Требуются подтверждённая кадровая история и согласование порогов с HR',
        'data_origin_counts':{str(k):int(v) for k,v in origins.items()},
        'calibration':{'method':calibration['method'],
                       'fit_employees':len(calibration['fit_employee_ids']),
                       'calibration_employees':len(calibration['calibration_employee_ids']),
                       'brier_before':compute_metrics(y,pipe.base.predict_proba(X)[:,1])['brier'],
                       'brier_after':compute_metrics(y,proba)['brier'],'bins':bins},
        'hr_capacity':{'fraction':0.1,'employees':top,'precision':float(y[idx].mean()),
                       'recall':float(y[idx].sum()/y.sum())},'temporal_backtest':temporal}
