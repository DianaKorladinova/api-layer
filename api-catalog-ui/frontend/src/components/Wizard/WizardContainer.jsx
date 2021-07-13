import { connect } from 'react-redux';
import WizardDialog from './WizardDialog';
import { wizardToggleDisplay } from '../../actions/wizard-actions';

const mapStateToProps = state => ({
    wizardIsOpen: state.wizardReducer.wizardIsOpen,
    inputData: state.wizardReducer.inputData,
});

const mapDispatchToProps = {
    wizardToggleDisplay,
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(WizardDialog);
