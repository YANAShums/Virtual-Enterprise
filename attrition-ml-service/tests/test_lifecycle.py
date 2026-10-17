from concurrent.futures import ThreadPoolExecutor
from threading import Event
from unittest.mock import patch,MagicMock
import pytest
from app import lifecycle
from app.models import ModelVersion,TrainingRun
from app.api_v2 import create_app
from app.config import Settings
from app.services import get_active_model


def test_operation_excludes_another_app_and_releases_after_failure():
    session=MagicMock();session.bind.dialect.name='sqlite'
    with lifecycle.operation(session):
        with pytest.raises(lifecycle.LifecycleBusy):
            with lifecycle.operation(session):pass
    with pytest.raises(ValueError):
        with lifecycle.operation(session):raise ValueError('failure')
    with lifecycle.operation(session):pass


def test_mysql_lock_owner_survives_session_commit_and_is_released():
    session=MagicMock();session.bind.dialect.name='mysql'
    conn=session.bind.connect.return_value
    conn.execute.return_value.scalar.return_value=1
    with lifecycle.operation(session):
        session.commit()
        assert not conn.close.called
    assert conn.execute.call_count==2
    assert 'RELEASE_LOCK' in str(conn.execute.call_args.args[0])
    conn.close.assert_called_once()


def test_lost_mysql_connection_cannot_leave_process_permanently_locked():
    session=MagicMock();session.bind.dialect.name='mysql'
    conn=session.bind.connect.return_value
    acquired=MagicMock();acquired.scalar.return_value=1
    conn.execute.side_effect=[acquired,RuntimeError('lost connection')]
    with pytest.raises(RuntimeError):
        with lifecycle.operation(session):pass
    session.bind.dialect.name='sqlite'
    with lifecycle.operation(session):pass


def test_request_key_reuses_candidate_without_retraining(tmp_path):
    app=create_app(Settings(database_url='sqlite:///:memory:',model_dir=str(tmp_path),api_key='k'))
    with app.state.session_factory() as s:
        model=ModelVersion(version='candidate',status='CANDIDATE',schema_version='s',file_path='x',file_sha256='sha',
            dataset_id='d',train_rows=1,test_rows=1,metrics={},gate_passed=True,gate_reasons=[])
        s.add(model);s.add(TrainingRun(request_key='request',status='SUCCESS',version='candidate'));s.commit()
        with patch('app.services.train_model') as train:
            assert lifecycle.train_candidate(s,app.state.settings,'request').version=='candidate'
            train.assert_not_called()


def test_corrupt_candidate_cannot_replace_active_model(tmp_path):
    app=create_app(Settings(database_url='sqlite:///:memory:',model_dir=str(tmp_path),api_key='k'))
    with app.state.session_factory() as s:
        for version,status in [('working','ACTIVE'),('bad','CANDIDATE')]:
            s.add(ModelVersion(version=version,status=status,schema_version='s',file_path=str(tmp_path/'missing'),file_sha256='sha',
                dataset_id='d',train_rows=1,test_rows=1,metrics={},gate_passed=True,gate_reasons=[]))
        s.commit()
        with pytest.raises(OSError):lifecycle.activate(s,'bad',app.state.settings)
        assert get_active_model(s).version=='working'


def test_corrected_legacy_import_reaches_training_but_cannot_overwrite_v2(tmp_path):
    from sqlalchemy import text,select
    from app.models import FeatureSnapshot
    from app.project_bridge import sync_legacy_imports
    from datetime import date
    app=create_app(Settings(database_url='sqlite:///:memory:',api_key='k'))
    with app.state.session_factory() as s:
        s.execute(text('CREATE TABLE attrition_feature_snapshots(employee_id TEXT,snapshot_date DATE,source TEXT,target_label INT,department_code TEXT,position_code TEXT)'))
        s.execute(text("INSERT INTO attrition_feature_snapshots VALUES ('old','2020-01-01','IMPORT',0,'IT','DEV')"));s.commit()
        assert sync_legacy_imports(s,date.today())==1
        s.execute(text('UPDATE attrition_feature_snapshots SET target_label=1'));s.commit()
        assert sync_legacy_imports(s,date.today())==1
        row=s.scalars(select(FeatureSnapshot)).one();assert row.target_label==1
        row.label_reason='imported';s.commit()
        s.execute(text('UPDATE attrition_feature_snapshots SET target_label=0'));s.commit()
        assert sync_legacy_imports(s,date.today())==0
        assert row.target_label==1
