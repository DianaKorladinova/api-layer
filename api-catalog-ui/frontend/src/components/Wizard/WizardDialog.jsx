import React, { Component } from 'react';
import TextInput from 'mineral-ui/TextInput';
import ButtonGroup from 'mineral-ui/ButtonGroup';
import {
    Dialog,
    DialogBody,
    DialogHeader,
    DialogTitle,
    DialogFooter,
    DialogActions,
    Button,
    Text,
    Select,
} from 'mineral-ui';
import './wizard.css';

export default class WizardDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {
            selectedIndex: 0,
            inputData: props.inputData,
        };
        this.handleInputChange = this.handleInputChange.bind(this);
        this.handleCategoryChange = this.handleCategoryChange.bind(this);
        this.getPrev = this.getPrev.bind(this);
        this.getNext = this.getNext.bind(this);
    }

    getPrev() {
        const index = this.state.selectedIndex;
        const len = this.state.inputData.length;
        this.setState({ selectedIndex: (index + len - 1) % len });
    }

    getNext() {
        const index = this.state.selectedIndex;
        const len = this.state.inputData.length;
        this.setState({ selectedIndex: (index + 1) % len });
    }

    handleInputChange(event) {
        const { name } = event.target;
        const inputData = [...this.state.inputData];
        const objectToChange = inputData[this.state.selectedIndex];
        inputData[this.state.selectedIndex] = {
            ...objectToChange,
            content: { ...objectToChange.content, [name]: event.target.value },
        };
        this.setState({ inputData });
    }

    handleCategoryChange(event) {
        for (let i = 0; i < this.state.inputData.length; i += 1) {
            if (this.state.inputData[i].text === event.text) {
                this.setState({ selectedIndex: i });
                break;
            }
        }
    }

    closeWizard = () => {
        const { wizardToggleDisplay } = this.props;
        wizardToggleDisplay();
    };

    loadInputs = () => {
        const dataAsObject = this.state.inputData[this.state.selectedIndex];
        if (
            dataAsObject.content === undefined ||
            dataAsObject.content === null ||
            Object.entries(dataAsObject.content).length === 0
        )
            return '';
        const selectedData = Object.entries(dataAsObject.content);
        let key = 0;
        return selectedData.map(item => {
            key += 1;
            return (
                <TextInput
                    size="large"
                    name={item[0]}
                    onChange={this.handleInputChange}
                    key={key}
                    placeholder={item[0]}
                    value={dataAsObject.content[item[0]]}
                />
            );
        });
    };

    render() {
        const { wizardIsOpen } = this.props;
        return (
            <div className="dialog">
                <Dialog id="wizard-dialog" isOpen={wizardIsOpen} closeOnClickOutside={false}>
                    <DialogHeader>
                        <DialogTitle>Onboard a New API</DialogTitle>
                    </DialogHeader>
                    <DialogBody>
                        <Text>This wizard will guide you through creating a correct YAML for your application.</Text>
                        <ButtonGroup aria-label="Optional compositions">
                            <Button onClick={this.getPrev}>Previous</Button>
                            <Button onClick={this.getNext}>Next</Button>
                            <Select
                                className="selector"
                                data={this.state.inputData}
                                selectedItem={this.state.inputData[this.state.selectedIndex]}
                                onChange={this.handleCategoryChange}
                            />
                        </ButtonGroup>
                        <div className="wizardForm"> {this.loadInputs()}</div>
                    </DialogBody>
                    <DialogFooter className="dialog-footer">
                        <DialogActions>
                            <Button size="medium" onClick={this.closeWizard}>
                                Cancel
                            </Button>
                            <Button size="medium">Save file</Button>
                        </DialogActions>
                    </DialogFooter>
                </Dialog>
            </div>
        );
    }
}
